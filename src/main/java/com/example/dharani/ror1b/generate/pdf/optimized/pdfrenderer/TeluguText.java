package com.example.dharani.ror1b.generate.pdf.optimized.pdfrenderer;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextHitInfo;
import java.awt.font.TextLayout;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.text.BreakIterator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shapes and wraps Telugu text for the PDF.
 *
 * Shaping is done by the JDK's own text layout engine (HarfBuzz) with the
 * bundled, free (SIL OFL) Noto Sans Telugu font - nothing has to be installed
 * or downloaded on any machine.
 *
 * Every line yields two things: - draw : the visible, correctly shaped glyphs
 * as vector shapes (one reusable shape per glyph); - textRuns : an invisible
 * Unicode text layer, positioned word by word, for select / copy / search /
 * highlight. Results are cached, so repeated values are shaped only once.
 */
final class TeluguText {

	/**
	 * One wrapped line. draw : operators drawing the line (baseline origin, y
	 * down). chained=true : "dx dy cm /glyph Do" chain, positions in 0.1 pt, ends
	 * at (endX10, endY10) relative to the origin - place it with a relative "cm"
	 * and do not wrap it in q/Q; chained=false: a filled path, wrap it in q ... Q.
	 * forms : names of the glyph shapes {@code draw} refers to. textRuns : "Tz Td
	 * &lt;unicode&gt; Tj" operators of the invisible text layer, to be placed
	 * inside BT..ET.
	 */
	record Line(String draw, boolean chained, long endX10, long endY10, List<String> forms, String textRuns, float size,
			float advance, float ascent, float height) {
	}

	private record Key(String text, boolean bold, float size, float width) {
	}

	/**
	 * Code points below this limit get exact glyph/width data in the invisible
	 * layer.
	 */
	private static final int LIMIT = 0x2600;
	private static final int DEFAULT_WIDTH = 600;
	private static final int CACHE_SIZE = 8192;
	private static final FontRenderContext FRC = new FontRenderContext(null, true, true);
	private static final Locale TELUGU = Locale.forLanguageTag("te");

	private final byte[] regularBytes = readResource("/fonts/NotoSansTelugu-Regular.ttf");
	private final byte[] boldBytes = readResource("/fonts/NotoSansTelugu-Bold.ttf");
	private final Font regular = create(regularBytes);
	private final Font bold = create(boldBytes);
	private final Font fallbackRegular = new Font(Font.SANS_SERIF, Font.PLAIN, 1);
	private final Font fallbackBold = new Font(Font.SANS_SERIF, Font.BOLD, 1);

	private final PdfWriter.TextFont textFont;
	private final int[] nominalWidth = new int[LIMIT]; // advance per code point, 1/1000 em
	private final Map<String, String> glyphForms = new ConcurrentHashMap<>(); // form name -> path operators

	private final Map<Key, List<Line>> cache = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Key, List<Line>> eldest) {
			return size() > CACHE_SIZE;
		}
	});

	TeluguText() {
		// 1. advance width of every code the Telugu font knows; everything else gets
		// DEFAULT_WIDTH
		java.util.Arrays.fill(nominalWidth, DEFAULT_WIDTH);
		StringBuilder widths = new StringBuilder("/W [");
		Font thousand = regular.deriveFont(1000f);
		for (int cp = 0x20; cp < LIMIT; cp++) {
			if (regular.canDisplay(cp)) {
				GlyphVector gv = thousand.createGlyphVector(FRC, new String(Character.toChars(cp)));
				int width = Math.round(gv.getGlyphMetrics(0).getAdvanceX());
				nominalWidth[cp] = width;
				widths.append(' ').append(cp).append(" [").append(width).append(']');
			}
		}
		widths.append(" ]");

		// 2. one invisible box glyph per distinct advance width; every code points at
		// the box of its width
		java.util.TreeSet<Integer> distinct = new java.util.TreeSet<>();
		for (int w : nominalWidth) {
			if (w > 0) {
				distinct.add(w);
			}
		}
		int[] advances = distinct.stream().mapToInt(Integer::intValue).toArray();
		Map<Integer, Integer> gidOfWidth = new java.util.HashMap<>();
		for (int i = 0; i < advances.length; i++) {
			gidOfWidth.put(advances[i], i + 1);
		}
		byte[] cidToGid = new byte[LIMIT * 2];
		for (int cp = 0; cp < LIMIT; cp++) {
			int gid = nominalWidth[cp] > 0 ? gidOfWidth.get(nominalWidth[cp]) : 0;
			cidToGid[cp * 2] = (byte) (gid >> 8);
			cidToGid[cp * 2 + 1] = (byte) gid;
		}
		try {
			byte[] boxes = GlyphlessFont.create(advances);
			textFont = new PdfWriter.TextFont("ROR-Text", PdfWriter.deflate(boxes), boxes.length,
					new int[] { 0, GlyphlessFont.DESCENT, 1000, GlyphlessFont.ASCENT }, cidToGid, widths.toString());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	PdfWriter.TextFont textFont() {
		return textFont;
	}

	/** Path operators of a glyph shape created earlier by {@link #lines}. */
	String glyphForm(String name) {
		return glyphForms.get(name);
	}

	List<Line> lines(String text, boolean isBold, float size, float width) {
		Key key = new Key(text == null ? "" : text, isBold, size, width);
		List<Line> hit = cache.get(key);
		if (hit != null) {
			return hit;
		}
		List<Line> computed = layout(key);
		cache.put(key, computed);
		return computed;
	}

	private List<Line> layout(Key key) {
		String s = Normalizer.normalize(key.text().strip(), Normalizer.Form.NFC);
		if (s.isEmpty()) {
			return List.of();
		}
		Font primary = (key.bold() ? bold : regular).deriveFont(key.size());
		Font fallback = (key.bold() ? fallbackBold : fallbackRegular).deriveFont(key.size());

		// Which characters does the Telugu font lack? Those go to the fallback font.
		boolean[] needsFallback = new boolean[s.length()];
		AttributedString attributed = new AttributedString(s);
		int i = 0;
		while (i < s.length()) {
			boolean ok = primary.canDisplay(s.codePointAt(i));
			int j = i + Character.charCount(s.codePointAt(i));
			while (j < s.length() && primary.canDisplay(s.codePointAt(j)) == ok) {
				j += Character.charCount(s.codePointAt(j));
			}
			attributed.addAttribute(TextAttribute.FONT, ok ? primary : fallback, i, j);
			for (int k = i; k < j; k++) {
				needsFallback[k] = !ok;
			}
			i = j;
		}

		char[] chars = s.toCharArray();
		AttributedCharacterIterator it = attributed.getIterator();
		LineBreakMeasurer measurer = new LineBreakMeasurer(it, BreakIterator.getLineInstance(TELUGU), FRC);
		List<Line> out = new ArrayList<>();
		while (measurer.getPosition() < it.getEndIndex()) {
			int start = measurer.getPosition();
			TextLayout tl = measurer.nextLayout(Math.max(key.width(), 1f));
			int end = measurer.getPosition();
			float height = tl.getAscent() + tl.getDescent() + tl.getLeading();
			String lineText = s.substring(start, end);

			boolean fallbackInLine = false;
			for (int k = start; k < end; k++) {
				fallbackInLine |= needsFallback[k];
			}
			String draw;
			List<String> forms;
			long[] endPos = { 0, 0 };
			if (fallbackInLine) { // e.g. Latin letters: draw the layout's own outline
				draw = toPath(tl.getOutline(null));
				forms = List.of();
			} else {
				GlyphVector gv = primary.layoutGlyphVector(FRC, chars, start, end, Font.LAYOUT_LEFT_TO_RIGHT);
				Set<String> used = new LinkedHashSet<>();
				draw = glyphOps(gv, primary, key.bold(), key.size(), used, endPos);
				forms = List.copyOf(used);
			}
			out.add(new Line(draw, !fallbackInLine, endPos[0], endPos[1], forms, textRuns(tl, lineText, key.size()),
					key.size(), tl.getAdvance(), tl.getAscent(), height));
		}
		return List.copyOf(out);
	}

	/**
	 * Shaped glyphs -> "dx dy cm /name Do" chain. {@code cm} accumulates, so every
	 * move is relative to the previous glyph. Positions are rounded to 0.1 pt as
	 * absolute values and the differences are written, so rounding errors never add
	 * up along a line. Each distinct (font, size, glyph) shape is created once.
	 *
	 * @param end receives the final offset (in 0.1 pt) the chain leaves the
	 *            coordinate system at
	 */
	private String glyphOps(GlyphVector gv, Font sized, boolean isBold, float size, Set<String> usedNames, long[] end) {
		StringBuilder sb = new StringBuilder(gv.getNumGlyphs() * 24);
		long px = 0;
		long py = 0;
		for (int g = 0; g < gv.getNumGlyphs(); g++) {
			int gid = gv.getGlyphCode(g);
			if (gid >= 0xFFFE) { // invisible / missing marker (e.g. ZWJ) - nothing to draw
				continue;
			}
			String name = (isBold ? "B" : "R") + Math.round(size * 10) + "_" + gid;
			String path = glyphForms.get(name);
			if (path == null) {
				Shape outline = sized.createGlyphVector(FRC, new int[] { gid }).getGlyphOutline(0);
				path = toPath(outline);
				glyphForms.put(name, path);
			}
			if (path.isEmpty()) { // blank glyph such as a space
				continue;
			}
			Point2D p = gv.getGlyphPosition(g);
			long x10 = Math.round(p.getX() * 10);
			long y10 = Math.round(p.getY() * 10);
			sb.append("1 0 0 1 ");
			tenths(sb, x10 - px);
			sb.append(' ');
			tenths(sb, y10 - py);
			sb.append(" cm /").append(name).append(" Do\n");
			usedNames.add(name);
			px = x10;
			py = y10;
		}
		end[0] = px;
		end[1] = py;
		return sb.toString();
	}

	/**
	 * Writes a value given in tenths, e.g. 53 -> "5.3", -4 -> "-0.4", 120 -> "12".
	 */
	static void tenths(StringBuilder sb, long v) {
		if (v < 0) {
			sb.append('-');
			v = -v;
		}
		sb.append(v / 10);
		if (v % 10 != 0) {
			sb.append('.').append(v % 10);
		}
	}

	/**
	 * Invisible Unicode text of one line, split after every space so each word
	 * (with its trailing space) is a run placed at its exact x position and
	 * stretched (Tz) to its exact shaped width. That is what makes selection and
	 * search highlights line up with the visible words.
	 */
	private String textRuns(TextLayout tl, String lineText, float size) {
		int n = lineText.length();
		StringBuilder sb = new StringBuilder(n * 6 + 64);
		float previousX = 0;
		int a = 0;
		while (a < n) {
			int b = a;
			while (b < n && lineText.charAt(b) != ' ') {
				b++;
			}
			if (b < n) {
				b++; // keep the space with its word
			}
			float x0 = caretX(tl, a);
			float x1 = caretX(tl, b);
			double nominal = 0;
			for (int k = a; k < b; k++) {
				char c = lineText.charAt(k);
				nominal += (c < LIMIT ? nominalWidth[c] : DEFAULT_WIDTH) / 1000.0 * size;
			}
			double tz = nominal > 0 && x1 > x0 ? 100.0 * (x1 - x0) / nominal : 100.0;
			tz = Math.max(5.0, Math.min(tz, 2000.0));

			PdfWriter.number(sb, tz);
			sb.append(" Tz ");
			PdfWriter.number(sb, x0 - previousX);
			sb.append(" 0 Td <");
			for (int k = a; k < b; k++) {
				char c = lineText.charAt(k);
				for (int shift = 12; shift >= 0; shift -= 4) {
					sb.append(Character.toUpperCase(Character.forDigit((c >> shift) & 0xF, 16)));
				}
			}
			sb.append("> Tj\n");
			previousX = x0;
			a = b;
		}
		return sb.toString();
	}

	/**
	 * x position (along the baseline) of the boundary before character
	 * {@code offset}; offset == length is the end of the last character.
	 * (beforeOffset(i) is the trailing edge of character i-1, which is exactly that
	 * boundary.)
	 */
	private static float caretX(TextLayout tl, int offset) {
		return tl.getCaretInfo(TextHitInfo.beforeOffset(offset))[0];
	}

	/** java.awt.Shape -> PDF path operators ending with a fill. */
	private static String toPath(Shape shape) {
		PathIterator pi = shape.getPathIterator(null);
		StringBuilder sb = new StringBuilder(256);
		double[] c = new double[6];
		double cx = 0;
		double cy = 0;
		double sx = 0;
		double sy = 0;
		boolean any = false;
		for (; !pi.isDone(); pi.next()) {
			switch (pi.currentSegment(c)) {
			case PathIterator.SEG_MOVETO -> {
				cx = sx = c[0];
				cy = sy = c[1];
				op(sb, "m", cx, cy);
				any = true;
			}
			case PathIterator.SEG_LINETO -> {
				cx = c[0];
				cy = c[1];
				op(sb, "l", cx, cy);
			}
			case PathIterator.SEG_QUADTO -> {
				double x1 = cx + 2.0 / 3.0 * (c[0] - cx);
				double y1 = cy + 2.0 / 3.0 * (c[1] - cy);
				double x2 = c[2] + 2.0 / 3.0 * (c[0] - c[2]);
				double y2 = c[3] + 2.0 / 3.0 * (c[1] - c[3]);
				op(sb, "c", x1, y1, x2, y2, c[2], c[3]);
				cx = c[2];
				cy = c[3];
			}
			case PathIterator.SEG_CUBICTO -> {
				op(sb, "c", c[0], c[1], c[2], c[3], c[4], c[5]);
				cx = c[4];
				cy = c[5];
			}
			case PathIterator.SEG_CLOSE -> {
				sb.append("h\n");
				cx = sx;
				cy = sy;
			}
			default -> {
			}
			}
		}
		if (!any) {
			return "";
		}
		sb.append(pi.getWindingRule() == PathIterator.WIND_EVEN_ODD ? "f*\n" : "f\n");
		return sb.toString();
	}

	private static void op(StringBuilder sb, String operator, double... v) {
		for (double d : v) {
			PdfWriter.number(sb, d);
			sb.append(' ');
		}
		sb.append(operator).append('\n');
	}

	/** FontBBox in 1/1000 em, read from the TrueType 'head' table. */
	private static int[] bbox(byte[] ttf) {
		ByteBuffer b = ByteBuffer.wrap(ttf);
		int tables = b.getShort(4) & 0xFFFF;
		for (int t = 0; t < tables; t++) {
			int rec = 12 + t * 16;
			if (b.getInt(rec) == 0x68656164) { // 'head'
				int off = b.getInt(rec + 8);
				int unitsPerEm = b.getShort(off + 18) & 0xFFFF;
				return new int[] { b.getShort(off + 36) * 1000 / unitsPerEm, b.getShort(off + 38) * 1000 / unitsPerEm,
						b.getShort(off + 40) * 1000 / unitsPerEm, b.getShort(off + 42) * 1000 / unitsPerEm };
			}
		}
		return new int[] { -500, -500, 1500, 1500 };
	}

	private static byte[] readResource(String resource) {
		try (InputStream in = TeluguText.class.getResourceAsStream(resource)) {
			if (in == null) {
				throw new IllegalStateException("Bundled font missing on classpath: " + resource);
			}
			return in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Font create(byte[] ttf) {
		try (InputStream in = new ByteArrayInputStream(ttf)) {
			return Font.createFont(Font.TRUETYPE_FONT, in);
		} catch (FontFormatException | IOException e) {
			throw new IllegalStateException("Cannot load bundled font", e);
		}
	}
}
