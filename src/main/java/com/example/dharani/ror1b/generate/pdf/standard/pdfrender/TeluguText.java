package com.example.dharani.ror1b.generate.pdf.standard.pdfrender;

/*
 * import java.awt.Font; import java.awt.FontFormatException; import
 * java.awt.Shape; import java.awt.font.FontRenderContext; import
 * java.awt.font.GlyphVector; import java.awt.font.LineBreakMeasurer; import
 * java.awt.font.TextAttribute; import java.awt.font.TextLayout; import
 * java.awt.geom.PathIterator; import java.awt.geom.Point2D; import
 * java.io.ByteArrayInputStream; import java.io.IOException; import
 * java.io.InputStream; import java.io.UncheckedIOException; import
 * java.nio.ByteBuffer; import java.text.AttributedCharacterIterator; import
 * java.text.AttributedString; import java.text.BreakIterator; import
 * java.text.Normalizer; import java.util.ArrayList; import
 * java.util.Collections; import java.util.LinkedHashMap; import java.util.List;
 * import java.util.Locale; import java.util.Map;
 * 
 */

/*
 * import java.awt.Font; import java.awt.FontFormatException; import
 * java.awt.Shape; import java.awt.font.FontRenderContext; import
 * java.awt.font.GlyphVector; import java.awt.font.LineBreakMeasurer; import
 * java.awt.font.TextAttribute; import java.awt.font.TextLayout; import
 * java.awt.geom.PathIterator; import java.awt.geom.Point2D; import
 * java.io.ByteArrayInputStream; import java.io.IOException; import
 * java.io.InputStream; import java.io.UncheckedIOException; import
 * java.nio.ByteBuffer; import java.text.AttributedCharacterIterator; import
 * java.text.AttributedString; import java.text.BreakIterator; import
 * java.text.Normalizer; import java.util.ArrayList; import
 * java.util.Collections; import java.util.LinkedHashMap; import java.util.List;
 * import java.util.Locale; import java.util.Map; import java.util.TreeMap;
 * import java.util.concurrent.ConcurrentHashMap;
 * 
 *//**
	 * Shapes and wraps Telugu text for the PDF.
	 *
	 * Shaping is done by the JDK's own text layout engine (HarfBuzz) with the
	 * bundled, free (SIL OFL) Noto Sans Telugu font - nothing has to be installed
	 * or downloaded on any machine. Normal lines are emitted as glyph ids of the
	 * embedded font (tiny output). A line that contains characters the Telugu font
	 * does not have (e.g. Latin letters) is drawn from the JDK sans-serif font as
	 * outlines.
	 *
	 * Copy / search: every line carries its real Unicode text ({@code actualText})
	 * and every embedded font gets a ToUnicode map, so the text can be selected,
	 * copied and searched in PDF viewers. Results are cached, so repeated values
	 * are shaped only once.
	 */
/*
 * final class TeluguText {
 * 
 *//**
	 * One wrapped line. glyphs=true : content = "dx dy Td &lt;gid&gt; Tj"
	 * operators, to be placed inside BT..ET with font/size set. glyphs=false:
	 * content = filled path with the baseline at (0,0), y pointing down. actualText
	 * : the line's real Unicode text as UTF-16BE hex ("FEFF..."), for copy /
	 * search.
	 */
/*
 * record Line(boolean glyphs, String content, String font, float size, float
 * advance, float ascent, float height, String actualText) { }
 * 
 * private record Key(String text, boolean bold, float size, float width) { }
 * 
 * private static final int CACHE_SIZE = 8192; private static final
 * FontRenderContext FRC = new FontRenderContext(null, true, true); private
 * static final Locale TELUGU = Locale.forLanguageTag("te");
 * 
 * private final byte[] regularBytes =
 * readResource("/fonts/NotoSansTelugu-Regular.ttf"); private final byte[]
 * boldBytes = readResource("/fonts/NotoSansTelugu-Bold.ttf"); private final
 * Font regular = create(regularBytes); private final Font bold =
 * create(boldBytes); private final Font fallbackRegular = new
 * Font(Font.SANS_SERIF, Font.PLAIN, 1); private final Font fallbackBold = new
 * Font(Font.SANS_SERIF, Font.BOLD, 1); private final List<PdfWriter.FontSpec>
 * fontSpecs;
 * 
 *//**
	 * glyph id -> Unicode text per font ([0] regular, [1] bold): from the font's
	 * own cmap, plus learned ligatures.
	 */
/*
 * private final List<Map<Integer, String>> baseMap; private final
 * List<Map<Integer, String>> learnedMap = List.of(new ConcurrentHashMap<>(),
 * new ConcurrentHashMap<>());
 * 
 * private final Map<Key, List<Line>> cache = Collections.synchronizedMap(new
 * LinkedHashMap<>(256, 0.75f, true) {
 * 
 * @Override protected boolean removeEldestEntry(Map.Entry<Key, List<Line>>
 * eldest) { return size() > CACHE_SIZE; } });
 * 
 * TeluguText() { baseMap = List.of(reverseCmap(regular), reverseCmap(bold));
 * try { fontSpecs = List.of( new PdfWriter.FontSpec("F1",
 * "NotoSansTelugu-Regular", PdfWriter.deflate(regularBytes),
 * regularBytes.length, bbox(regularBytes), () -> toUnicodeCMap(0)), new
 * PdfWriter.FontSpec("F2", "NotoSansTelugu-Bold", PdfWriter.deflate(boldBytes),
 * boldBytes.length, bbox(boldBytes), () -> toUnicodeCMap(1))); } catch
 * (IOException e) { throw new UncheckedIOException(e); } }
 * 
 * List<PdfWriter.FontSpec> fontSpecs() { return fontSpecs; }
 * 
 * List<Line> lines(String text, boolean isBold, float size, float width) { Key
 * key = new Key(text == null ? "" : text, isBold, size, width); List<Line> hit
 * = cache.get(key); if (hit != null) { return hit; } List<Line> computed =
 * layout(key); cache.put(key, computed); return computed; }
 * 
 * private List<Line> layout(Key key) { String s =
 * Normalizer.normalize(key.text().strip(), Normalizer.Form.NFC); if
 * (s.isEmpty()) { return List.of(); } Font primary = (key.bold() ? bold :
 * regular).deriveFont(key.size()); Font fallback = (key.bold() ? fallbackBold :
 * fallbackRegular).deriveFont(key.size()); String fontRes = key.bold() ? "F2" :
 * "F1";
 * 
 * // Which characters does the Telugu font lack? Those go to the fallback font.
 * boolean[] needsFallback = new boolean[s.length()]; AttributedString
 * attributed = new AttributedString(s); int i = 0; while (i < s.length()) {
 * boolean ok = primary.canDisplay(s.codePointAt(i)); int j = i +
 * Character.charCount(s.codePointAt(i)); while (j < s.length() &&
 * primary.canDisplay(s.codePointAt(j)) == ok) { j +=
 * Character.charCount(s.codePointAt(j)); }
 * attributed.addAttribute(TextAttribute.FONT, ok ? primary : fallback, i, j);
 * for (int k = i; k < j; k++) { needsFallback[k] = !ok; } i = j; }
 * 
 * char[] chars = s.toCharArray(); AttributedCharacterIterator it =
 * attributed.getIterator(); LineBreakMeasurer measurer = new
 * LineBreakMeasurer(it, BreakIterator.getLineInstance(TELUGU), FRC); List<Line>
 * out = new ArrayList<>(); while (measurer.getPosition() < it.getEndIndex()) {
 * int start = measurer.getPosition(); TextLayout tl =
 * measurer.nextLayout(Math.max(key.width(), 1f)); int end =
 * measurer.getPosition(); float height = tl.getAscent() + tl.getDescent() +
 * tl.getLeading(); String actual = utf16Hex(s.substring(start, end));
 * 
 * boolean fallbackInLine = false; for (int k = start; k < end; k++) {
 * fallbackInLine |= needsFallback[k]; } if (fallbackInLine) { out.add(new
 * Line(false, toPath(tl.getOutline(null)), fontRes, key.size(),
 * tl.getAdvance(), tl.getAscent(), height, actual)); } else { GlyphVector gv =
 * primary.layoutGlyphVector(FRC, chars, start, end, Font.LAYOUT_LEFT_TO_RIGHT);
 * learn(key.bold() ? 1 : 0, gv, chars, start, end); out.add(new Line(true,
 * glyphOps(gv), fontRes, key.size(), tl.getAdvance(), tl.getAscent(), height,
 * actual)); } } return List.copyOf(out); }
 * 
 *//**
	 * Shaped glyph ids + positions -> "dx dy Td <gid> Tj" chain (each Td is
	 * relative to the previous glyph).
	 */
/*
 * private static String glyphOps(GlyphVector gv) { StringBuilder sb = new
 * StringBuilder(gv.getNumGlyphs() * 22); double px = 0; double py = 0; for (int
 * g = 0; g < gv.getNumGlyphs(); g++) { int gid = gv.getGlyphCode(g); if (gid >=
 * 0xFFFE) { // invisible / missing marker (e.g. ZWJ) - nothing to draw
 * continue; } Point2D p = gv.getGlyphPosition(g); PdfWriter.number(sb, p.getX()
 * - px); sb.append(' '); PdfWriter.number(sb, -(p.getY() - py)); // Java y
 * points down, PDF text space y points up sb.append(" Td <"); appendHex4(sb,
 * gid); sb.append("> Tj\n"); px = p.getX(); py = p.getY(); } return
 * sb.toString(); }
 * 
 * ---------------- copy / search support ----------------
 * 
 *//**
	 * UTF-16BE hex with byte-order mark: the PDF text-string form used by
	 * /ActualText.
	 */
/*
 * static String utf16Hex(String text) { StringBuilder sb = new StringBuilder(4
 * + text.length() * 4).append("FEFF"); for (int i = 0; i < text.length(); i++)
 * { appendHex4(sb, text.charAt(i)); } return sb.toString(); }
 * 
 * private static void appendHex4(StringBuilder sb, int v) { for (int shift =
 * 12; shift >= 0; shift -= 4) {
 * sb.append(Character.toUpperCase(Character.forDigit((v >> shift) & 0xF, 16)));
 * } }
 * 
 *//**
	 * glyph id -> the character the font's cmap assigns to it (lowest code point
	 * wins).
	 */
/*
 * private static Map<Integer, String> reverseCmap(Font font) { Map<Integer,
 * String> map = new ConcurrentHashMap<>(); for (int cp = 0x20; cp < 0x2600;
 * cp++) { if (font.canDisplay(cp)) { String s = new
 * String(Character.toChars(cp)); map.putIfAbsent(font.createGlyphVector(FRC,
 * s).getGlyphCode(0), s); } } return map; }
 * 
 *//**
	 * Learns what glyphs that are NOT in the cmap (conjunct ligatures, below-base
	 * forms) stand for. The shaper reports which characters each cluster of glyphs
	 * came from; characters already explained by plain letter glyphs are removed,
	 * and what is left belongs to the single remaining glyph. Best effort for
	 * viewers that ignore /ActualText; /ActualText itself is always exact.
	 */
/*
 * private void learn(int font, GlyphVector gv, char[] chars, int start, int
 * end) { Map<Integer, String> base = baseMap.get(font); int n =
 * gv.getNumGlyphs(); int i = 0; while (i < n) { int clusterStart =
 * gv.getGlyphCharIndex(i); // index relative to 'start' int j = i + 1; while (j
 * < n && gv.getGlyphCharIndex(j) == clusterStart) { j++; } int clusterEnd = j <
 * n ? gv.getGlyphCharIndex(j) : end - start; if (clusterEnd > clusterStart) {
 * StringBuilder leftover = new StringBuilder( new String(chars, start +
 * clusterStart, clusterEnd - clusterStart)); Integer unmapped = null; boolean
 * consistent = true; for (int g = i; g < j && consistent; g++) { int gid =
 * gv.getGlyphCode(g); if (gid >= 0xFFFE) { continue; } String known =
 * base.get(gid); if (known == null) { consistent = unmapped == null; // two
 * unknown glyphs cannot be split reliably unmapped = gid; } else { int at =
 * leftover.indexOf(known); if (at < 0) { consistent = false; } else {
 * leftover.delete(at, at + known.length()); } } } if (consistent && unmapped !=
 * null && leftover.length() > 0) { learnedMap.get(font).putIfAbsent(unmapped,
 * leftover.toString()); } } i = j; } }
 * 
 *//**
	 * ToUnicode CMap text for one font: every plain glyph plus every ligature
	 * learned so far.
	 */
/*
 * private String toUnicodeCMap(int font) { TreeMap<Integer, String> all = new
 * TreeMap<>(learnedMap.get(font)); all.putAll(baseMap.get(font)); // the font's
 * own cmap always wins StringBuilder sb = new StringBuilder(8192);
 * sb.append("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n")
 * .append("/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n"
 * ) .append("/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n")
 * .append("1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange\n");
 * List<Map.Entry<Integer, String>> entries = new ArrayList<>(all.entrySet());
 * for (int from = 0; from < entries.size(); from += 100) { int to =
 * Math.min(from + 100, entries.size()); sb.append(to -
 * from).append(" beginbfchar\n"); for (Map.Entry<Integer, String> e :
 * entries.subList(from, to)) { sb.append('<'); appendHex4(sb, e.getKey());
 * sb.append("> <"); for (char c : e.getValue().toCharArray()) { appendHex4(sb,
 * c); } sb.append(">\n"); } sb.append("endbfchar\n"); } return sb.
 * append("endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n").
 * toString(); }
 * 
 * ---------------- outlines (fallback font) ----------------
 * 
 *//** java.awt.Shape -> PDF path operators ending with a fill. */
/*
 * private static String toPath(Shape shape) { PathIterator pi =
 * shape.getPathIterator(null); StringBuilder sb = new StringBuilder(256);
 * double[] c = new double[6]; double cx = 0; double cy = 0; double sx = 0;
 * double sy = 0; boolean any = false; for (; !pi.isDone(); pi.next()) { switch
 * (pi.currentSegment(c)) { case PathIterator.SEG_MOVETO -> { cx = sx = c[0]; cy
 * = sy = c[1]; op(sb, "m", cx, cy); any = true; } case PathIterator.SEG_LINETO
 * -> { cx = c[0]; cy = c[1]; op(sb, "l", cx, cy); } case
 * PathIterator.SEG_QUADTO -> { double x1 = cx + 2.0 / 3.0 * (c[0] - cx); double
 * y1 = cy + 2.0 / 3.0 * (c[1] - cy); double x2 = c[2] + 2.0 / 3.0 * (c[0] -
 * c[2]); double y2 = c[3] + 2.0 / 3.0 * (c[1] - c[3]); op(sb, "c", x1, y1, x2,
 * y2, c[2], c[3]); cx = c[2]; cy = c[3]; } case PathIterator.SEG_CUBICTO -> {
 * op(sb, "c", c[0], c[1], c[2], c[3], c[4], c[5]); cx = c[4]; cy = c[5]; } case
 * PathIterator.SEG_CLOSE -> { sb.append("h\n"); cx = sx; cy = sy; } default ->
 * { } } } if (!any) { return ""; } sb.append(pi.getWindingRule() ==
 * PathIterator.WIND_EVEN_ODD ? "f*\n" : "f\n"); return sb.toString(); }
 * 
 * private static void op(StringBuilder sb, String operator, double... v) { for
 * (double d : v) { PdfWriter.number(sb, d); sb.append(' '); }
 * sb.append(operator).append('\n'); }
 * 
 *//** FontBBox in 1/1000 em, read from the TrueType 'head' table. *//*
																		 * private static int[] bbox(byte[] ttf) {
																		 * ByteBuffer b = ByteBuffer.wrap(ttf); int
																		 * tables = b.getShort(4) & 0xFFFF; for (int t =
																		 * 0; t < tables; t++) { int rec = 12 + t * 16;
																		 * if (b.getInt(rec) == 0x68656164) { // 'head'
																		 * int off = b.getInt(rec + 8); int unitsPerEm =
																		 * b.getShort(off + 18) & 0xFFFF; return new
																		 * int[] { b.getShort(off + 36) * 1000 /
																		 * unitsPerEm, b.getShort(off + 38) * 1000 /
																		 * unitsPerEm, b.getShort(off + 40) * 1000 /
																		 * unitsPerEm, b.getShort(off + 42) * 1000 /
																		 * unitsPerEm }; } } return new int[] { -500,
																		 * -500, 1500, 1500 }; }
																		 * 
																		 * private static byte[] readResource(String
																		 * resource) { try (InputStream in =
																		 * TeluguText.class.getResourceAsStream(resource
																		 * )) { if (in == null) { throw new
																		 * IllegalStateException("Bundled font missing on classpath: "
																		 * + resource); } return in.readAllBytes(); }
																		 * catch (IOException e) { throw new
																		 * UncheckedIOException(e); } }
																		 * 
																		 * private static Font create(byte[] ttf) { try
																		 * (InputStream in = new
																		 * ByteArrayInputStream(ttf)) { return
																		 * Font.createFont(Font.TRUETYPE_FONT, in); }
																		 * catch (FontFormatException | IOException e) {
																		 * throw new
																		 * IllegalStateException("Cannot load bundled font"
																		 * , e); } } }
																		 */
/**
 * Shapes and wraps Telugu text for the PDF.
 *
 * Shaping is done by the JDK's own text layout engine (HarfBuzz) with the
 * bundled, free (SIL OFL) Noto Sans Telugu font - nothing has to be installed
 * or downloaded on any machine. Normal lines are emitted as glyph ids of the
 * embedded font (tiny output). A line that contains characters the Telugu font
 * does not have (e.g. Latin letters) is drawn from the JDK sans-serif font as
 * outlines. Results are cached, so repeated values are shaped only once.
 */
/*
 * final class TeluguText {
 * 
 *//**
	 * One wrapped line. glyphs=true : content = "dx dy Td &lt;gid&gt; Tj"
	 * operators, to be placed inside BT..ET with font/size set. glyphs=false:
	 * content = filled path with the baseline at (0,0), y pointing down.
	 */
/*
 * record Line(boolean glyphs, String content, String font, float size, float
 * advance, float ascent, float height) { }
 * 
 * private record Key(String text, boolean bold, float size, float width) { }
 * 
 * private static final int CACHE_SIZE = 8192; private static final
 * FontRenderContext FRC = new FontRenderContext(null, true, true); private
 * static final Locale TELUGU = Locale.forLanguageTag("te");
 * 
 * private final byte[] regularBytes =
 * readResource("/fonts/NotoSansTelugu-Regular.ttf"); private final byte[]
 * boldBytes = readResource("/fonts/NotoSansTelugu-Bold.ttf"); private final
 * Font regular = create(regularBytes); private final Font bold =
 * create(boldBytes); private final Font fallbackRegular = new
 * Font(Font.SANS_SERIF, Font.PLAIN, 1); private final Font fallbackBold = new
 * Font(Font.SANS_SERIF, Font.BOLD, 1); private final List<PdfWriter.FontSpec>
 * fontSpecs;
 * 
 * private final Map<Key, List<Line>> cache = Collections.synchronizedMap(new
 * LinkedHashMap<>(256, 0.75f, true) {
 * 
 * @Override protected boolean removeEldestEntry(Map.Entry<Key, List<Line>>
 * eldest) { return size() > CACHE_SIZE; } });
 * 
 * TeluguText() { try { fontSpecs = List.of( new PdfWriter.FontSpec("F1",
 * "NotoSansTelugu-Regular", PdfWriter.deflate(regularBytes),
 * regularBytes.length, bbox(regularBytes)), new PdfWriter.FontSpec("F2",
 * "NotoSansTelugu-Bold", PdfWriter.deflate(boldBytes), boldBytes.length,
 * bbox(boldBytes))); } catch (IOException e) { throw new
 * UncheckedIOException(e); } }
 * 
 * List<PdfWriter.FontSpec> fontSpecs() { return fontSpecs; }
 * 
 * List<Line> lines(String text, boolean isBold, float size, float width) { Key
 * key = new Key(text == null ? "" : text, isBold, size, width); List<Line> hit
 * = cache.get(key); if (hit != null) { return hit; } List<Line> computed =
 * layout(key); cache.put(key, computed); return computed; }
 * 
 * private List<Line> layout(Key key) { String s =
 * Normalizer.normalize(key.text().strip(), Normalizer.Form.NFC); if
 * (s.isEmpty()) { return List.of(); } Font primary = (key.bold() ? bold :
 * regular).deriveFont(key.size()); Font fallback = (key.bold() ? fallbackBold :
 * fallbackRegular).deriveFont(key.size()); String fontRes = key.bold() ? "F2" :
 * "F1";
 * 
 * // Which characters does the Telugu font lack? Those go to the fallback font.
 * boolean[] needsFallback = new boolean[s.length()]; AttributedString
 * attributed = new AttributedString(s); int i = 0; while (i < s.length()) {
 * boolean ok = primary.canDisplay(s.codePointAt(i)); int j = i +
 * Character.charCount(s.codePointAt(i)); while (j < s.length() &&
 * primary.canDisplay(s.codePointAt(j)) == ok) { j +=
 * Character.charCount(s.codePointAt(j)); }
 * attributed.addAttribute(TextAttribute.FONT, ok ? primary : fallback, i, j);
 * for (int k = i; k < j; k++) { needsFallback[k] = !ok; } i = j; }
 * 
 * char[] chars = s.toCharArray(); AttributedCharacterIterator it =
 * attributed.getIterator(); LineBreakMeasurer measurer = new
 * LineBreakMeasurer(it, BreakIterator.getLineInstance(TELUGU), FRC); List<Line>
 * out = new ArrayList<>(); while (measurer.getPosition() < it.getEndIndex()) {
 * int start = measurer.getPosition(); TextLayout tl =
 * measurer.nextLayout(Math.max(key.width(), 1f)); int end =
 * measurer.getPosition(); float height = tl.getAscent() + tl.getDescent() +
 * tl.getLeading();
 * 
 * boolean fallbackInLine = false; for (int k = start; k < end; k++) {
 * fallbackInLine |= needsFallback[k]; } if (fallbackInLine) { out.add(new
 * Line(false, toPath(tl.getOutline(null)), fontRes, key.size(),
 * tl.getAdvance(), tl.getAscent(), height)); } else { GlyphVector gv =
 * primary.layoutGlyphVector(FRC, chars, start, end, Font.LAYOUT_LEFT_TO_RIGHT);
 * out.add(new Line(true, glyphOps(gv), fontRes, key.size(), tl.getAdvance(),
 * tl.getAscent(), height)); } } return List.copyOf(out); }
 * 
 *//**
	 * Shaped glyph ids + positions -> "dx dy Td <gid> Tj" chain (each Td is
	 * relative to the previous glyph).
	 */
/*
 * private static String glyphOps(GlyphVector gv) { StringBuilder sb = new
 * StringBuilder(gv.getNumGlyphs() * 22); double px = 0; double py = 0; for (int
 * g = 0; g < gv.getNumGlyphs(); g++) { int gid = gv.getGlyphCode(g); if (gid >=
 * 0xFFFE) { // invisible / missing marker (e.g. ZWJ) - nothing to draw
 * continue; } Point2D p = gv.getGlyphPosition(g); PdfWriter.number(sb, p.getX()
 * - px); sb.append(' '); PdfWriter.number(sb, -(p.getY() - py)); // Java y
 * points down, PDF text space y points up sb.append(" Td <"); for (int shift =
 * 12; shift >= 0; shift -= 4) { sb.append(Character.forDigit((gid >> shift) &
 * 0xF, 16)); } sb.append("> Tj\n"); px = p.getX(); py = p.getY(); } return
 * sb.toString(); }
 * 
 *//** java.awt.Shape -> PDF path operators ending with a fill. */
/*
 * private static String toPath(Shape shape) { PathIterator pi =
 * shape.getPathIterator(null); StringBuilder sb = new StringBuilder(256);
 * double[] c = new double[6]; double cx = 0; double cy = 0; double sx = 0;
 * double sy = 0; boolean any = false; for (; !pi.isDone(); pi.next()) { switch
 * (pi.currentSegment(c)) { case PathIterator.SEG_MOVETO -> { cx = sx = c[0]; cy
 * = sy = c[1]; op(sb, "m", cx, cy); any = true; } case PathIterator.SEG_LINETO
 * -> { cx = c[0]; cy = c[1]; op(sb, "l", cx, cy); } case
 * PathIterator.SEG_QUADTO -> { double x1 = cx + 2.0 / 3.0 * (c[0] - cx); double
 * y1 = cy + 2.0 / 3.0 * (c[1] - cy); double x2 = c[2] + 2.0 / 3.0 * (c[0] -
 * c[2]); double y2 = c[3] + 2.0 / 3.0 * (c[1] - c[3]); op(sb, "c", x1, y1, x2,
 * y2, c[2], c[3]); cx = c[2]; cy = c[3]; } case PathIterator.SEG_CUBICTO -> {
 * op(sb, "c", c[0], c[1], c[2], c[3], c[4], c[5]); cx = c[4]; cy = c[5]; } case
 * PathIterator.SEG_CLOSE -> { sb.append("h\n"); cx = sx; cy = sy; } default ->
 * { } } } if (!any) { return ""; } sb.append(pi.getWindingRule() ==
 * PathIterator.WIND_EVEN_ODD ? "f*\n" : "f\n"); return sb.toString(); }
 * 
 * private static void op(StringBuilder sb, String operator, double... v) { for
 * (double d : v) { PdfWriter.number(sb, d); sb.append(' '); }
 * sb.append(operator).append('\n'); }
 * 
 *//** FontBBox in 1/1000 em, read from the TrueType 'head' table. *//*
																		 * private static int[] bbox(byte[] ttf) {
																		 * ByteBuffer b = ByteBuffer.wrap(ttf); int
																		 * tables = b.getShort(4) & 0xFFFF; for (int t =
																		 * 0; t < tables; t++) { int rec = 12 + t * 16;
																		 * if (b.getInt(rec) == 0x68656164) { // 'head'
																		 * int off = b.getInt(rec + 8); int unitsPerEm =
																		 * b.getShort(off + 18) & 0xFFFF; return new
																		 * int[] { b.getShort(off + 36) * 1000 /
																		 * unitsPerEm, b.getShort(off + 38) * 1000 /
																		 * unitsPerEm, b.getShort(off + 40) * 1000 /
																		 * unitsPerEm, b.getShort(off + 42) * 1000 /
																		 * unitsPerEm }; } } return new int[] { -500,
																		 * -500, 1500, 1500 }; }
																		 * 
																		 * private static byte[] readResource(String
																		 * resource) { try (InputStream in =
																		 * TeluguText.class.getResourceAsStream(resource
																		 * )) { if (in == null) { throw new
																		 * IllegalStateException("Bundled font missing on classpath: "
																		 * + resource); } return in.readAllBytes(); }
																		 * catch (IOException e) { throw new
																		 * UncheckedIOException(e); } }
																		 * 
																		 * private static Font create(byte[] ttf) { try
																		 * (InputStream in = new
																		 * ByteArrayInputStream(ttf)) { return
																		 * Font.createFont(Font.TRUETYPE_FONT, in); }
																		 * catch (FontFormatException | IOException e) {
																		 * throw new
																		 * IllegalStateException("Cannot load bundled font"
																		 * , e); } } }
																		 */

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
	 * One wrapped line. draw : operators drawing the line, to be placed after "q 1
	 * 0 0 1 x y cm" (baseline origin, y down). forms : names of the glyph shapes
	 * {@code draw} refers to. textRuns : "Tz Td &lt;unicode&gt; Tj" operators of
	 * the invisible text layer, to be placed inside BT..ET.
	 */
	record Line(String draw, List<String> forms, String textRuns, float size, float advance, float ascent,
			float height) {
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
		java.util.Arrays.fill(nominalWidth, DEFAULT_WIDTH);
		byte[] cidToGid = new byte[LIMIT * 2];
		StringBuilder widths = new StringBuilder("/W [");
		Font thousand = regular.deriveFont(1000f);
		for (int cp = 0x20; cp < LIMIT; cp++) {
			if (regular.canDisplay(cp)) {
				GlyphVector gv = thousand.createGlyphVector(FRC, new String(Character.toChars(cp)));
				int gid = gv.getGlyphCode(0);
				int width = Math.round(gv.getGlyphMetrics(0).getAdvanceX());
				cidToGid[cp * 2] = (byte) (gid >> 8);
				cidToGid[cp * 2 + 1] = (byte) gid;
				nominalWidth[cp] = width;
				widths.append(' ').append(cp).append(" [").append(width).append(']');
			}
		}
		widths.append(" ]");
		try {
			textFont = new PdfWriter.TextFont("NotoSansTelugu-Regular", PdfWriter.deflate(regularBytes),
					regularBytes.length, bbox(regularBytes), cidToGid, widths.toString());
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
			if (fallbackInLine) { // e.g. Latin letters: draw the layout's own outline
				draw = toPath(tl.getOutline(null));
				forms = List.of();
			} else {
				GlyphVector gv = primary.layoutGlyphVector(FRC, chars, start, end, Font.LAYOUT_LEFT_TO_RIGHT);
				Set<String> used = new LinkedHashSet<>();
				draw = glyphOps(gv, primary, key.bold(), key.size(), used);
				forms = List.copyOf(used);
			}
			out.add(new Line(draw, forms, textRuns(tl, lineText, key.size()), key.size(), tl.getAdvance(),
					tl.getAscent(), height));
		}
		return List.copyOf(out);
	}

	/**
	 * Shaped glyphs -> "dx dy cm /name Do" chain. {@code cm} accumulates, so every
	 * move is relative to the previous glyph. Each distinct (font, size, glyph)
	 * shape is created once and shared by all lines.
	 */
	private String glyphOps(GlyphVector gv, Font sized, boolean isBold, float size, Set<String> usedNames) {
		StringBuilder sb = new StringBuilder(gv.getNumGlyphs() * 28);
		double px = 0;
		double py = 0;
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
			sb.append("1 0 0 1 ");
			PdfWriter.number(sb, p.getX() - px);
			sb.append(' ');
			PdfWriter.number(sb, p.getY() - py);
			sb.append(" cm /").append(name).append(" Do\n");
			usedNames.add(name);
			px = p.getX();
			py = p.getY();
		}
		return sb.toString();
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
			float x0 = caretX(tl, a, n);
			float x1 = caretX(tl, b, n);
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
	 * {@code offset}.
	 */
	private static float caretX(TextLayout tl, int offset, int length) {
		TextHitInfo hit = offset >= length ? TextHitInfo.afterOffset(length - 1) : TextHitInfo.beforeOffset(offset);
		return tl.getCaretInfo(hit)[0];
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