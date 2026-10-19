package com.example.dharani.ror1b.generate.pdf.optimized.pdfrenderer;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.dharani.common.model.Ror1BLandDetails;

/**
 * Draws the Telugu "1-B (ROR)" register as a multi-page A4 PDF. Pure Java: no
 * browser, no native libraries, no downloads. Pages are streamed to the output
 * as they fill up, so a very large number of rows needs only a few MB of
 * memory.
 *
 * Every page is written in three parts: the visible glyph shapes, the table
 * grid (a few long lines instead of one rectangle per cell), and one invisible
 * text object holding the real Unicode text (select / copy / search).
 */
@Component("lessSizeRor1BPdfRenderer")
public class Ror1BPdfRenderer {

	private static final String TITLE_1 = "తెలంగాణ ప్రభుత్వము, రెవెన్యూశాఖ, భూమి రికార్డుల కంప్యూటీకరణ";
	private static final String TITLE_2 = "1-బి నమూనా(ROR)";
	private static final String AREA_UNITS = "ఏ./గుం.";
	private static final String[] HEADERS = { "వరుస నెం.", "పట్టాదారు పేరు", "(తండ్రి/ భర్త పేరు)", "ఖాతా నెంబరు",
			"సర్వే నెంబర్", "భూమి వివరణ", "మొత్తం విస్తీర్ణము", "భూమి స్వభావం" };
	private static final String[] NUMBERS = { "(1)", "(2)", "(3)", "(4)", "(5)", "(6)", "(7)", "(8)" };
	/**
	 * Land classification as printed on the register; unknown values are printed as
	 * they are.
	 */
	private static final Map<String, String> LAND_DESCRIPTION = Map.of("Metta/ Dry", "మెట్ట/ఖుష్కి");

	private static final float PAGE_W = 595.28f;
	private static final float PAGE_H = 841.89f;
	private static final float MARGIN_X = 40f;
	private static final float MARGIN_TOP = 45f;
	private static final float MARGIN_BOTTOM = 45f;
	private static final float CONTENT_W = PAGE_W - 2 * MARGIN_X;
	private static final float[] COL_W = { 34f, 98f, 88f, 46f, 54f, 65f, 56f, 74.28f };
	private static final float PAD_X = 4f;
	private static final float PAD_Y = 4f;
	private static final float BODY_SIZE = 9f;

	private enum Align {
		LEFT, CENTER, RIGHT
	}

	private final TeluguText text = new TeluguText(); // loads the bundled fonts once, fails fast

	/** Writes the PDF to {@code out} (flushed, not closed). */
	public void render(Ror1BHeader header, List<Ror1BLandDetails> rows, OutputStream out) throws IOException {
		PdfWriter pdf = new PdfWriter(new BufferedOutputStream(out, 64 * 1024), text.textFont());
		Sheet sheet = new Sheet(pdf);
		sheet.newPage();
		sheet.titleBlock(header);
		sheet.tableHeader();
		int n = 1;
		for (Ror1BLandDetails r : rows) {
			sheet.bodyRow(cells(n++, r));
		}
		sheet.close();
	}

	private static String[] cells(int serial, Ror1BLandDetails r) {
		Map<String, String> p = r.pattadarDetails();
		Map<String, String> l = r.landDetails();
		String nature = l.getOrDefault("Classification of Land", "");
		return new String[] { Integer.toString(serial), beforeSlash(p.get("Pattadar / Authorised Person Name")),
				beforeSlash(p.get("Father / Husband's Name")), nullToEmpty(r.khataNumber()), nullToEmpty(r.surveyNo()),
				LAND_DESCRIPTION.getOrDefault(nature, nature), nullToEmpty(l.get("Total Extent (Ac. Gts)")),
				nullToEmpty(l.get("Nature of Land")) };
	}

	/** "వంగ బాల్ రెడ్డి / VANGA BAL REDDY" -> "వంగ బాల్ రెడ్డి" */
	static String beforeSlash(String v) {
		if (v == null) {
			return "";
		}
		int i = v.indexOf(" / ");
		return (i >= 0 ? v.substring(0, i) : v).trim();
	}

	private static String nullToEmpty(String s) {
		return s == null ? "" : s;
	}

	/** Page-level drawing state. */
	private final class Sheet {

		private final PdfWriter pdf;
		private final Set<String> usedForms = new LinkedHashSet<>();
		/**
		 * How often each line was drawn; bounded (least recently used goes first) so
		 * huge files stay small in memory.
		 */
		private final Map<TeluguText.Line, int[]> uses = new LinkedHashMap<>(1024, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<TeluguText.Line, int[]> eldest) {
				return size() > MAX_TRACKED_LINES;
			}
		};
		private final Map<TeluguText.Line, String> lineFormNames = new LinkedHashMap<>();
		private final Map<String, PdfWriter.LineForm> lineForms = new LinkedHashMap<>();

		private StringBuilder visible; // glyph shapes, drawn in a y-down coordinate system
		private StringBuilder invisible; // text runs of the invisible Unicode layer (normal PDF coordinates)
		private long chainX; // where the visible "cm" chain currently stands, in 0.1 pt
		private long chainY;
		private float textSize; // font size currently set in the invisible text object
		private float y; // current layout position, y down
		private final List<Float> rowEdges = new ArrayList<>(); // table horizontal lines of this page

		Sheet(PdfWriter pdf) {
			this.pdf = pdf;
		}

		void newPage() throws IOException {
			flushPage();
			visible = new StringBuilder(48 * 1024);
			invisible = new StringBuilder(16 * 1024);
			chainX = 0;
			chainY = 0;
			textSize = -1;
			rowEdges.clear();
			y = MARGIN_TOP;
		}

		void close() throws IOException {
			flushPage();
			Map<String, String> forms = new LinkedHashMap<>();
			for (String name : usedForms) {
				forms.put(name, text.glyphForm(name));
			}
			pdf.finish("ROR 1-B", forms, lineForms);
		}

		private void flushPage() throws IOException {
			if (visible == null) {
				return;
			}
			StringBuilder page = new StringBuilder(visible.length() + invisible.length() + 1024);
			page.append("0 g 0.25 G 0.5 w\nq 1 0 0 -1 0 ");
			PdfWriter.number(page, PAGE_H);
			page.append(" cm\n").append(visible).append("Q\n");
			grid(page);
			page.append("BT 3 Tr\n").append(invisible).append("ET\n");
			pdf.addPage(PAGE_W, PAGE_H, page);
			visible = null;
		}

		/**
		 * Table lines: one long line per row edge and one per column edge, instead of a
		 * rectangle per cell.
		 */
		private void grid(StringBuilder page) {
			if (rowEdges.size() < 2) {
				return;
			}
			float tableW = 0;
			for (float w : COL_W) {
				tableW += w;
			}
			for (float edge : rowEdges) {
				line(page, MARGIN_X, PAGE_H - edge, MARGIN_X + tableW, PAGE_H - edge);
			}
			float top = PAGE_H - rowEdges.get(0);
			float bottom = PAGE_H - rowEdges.get(rowEdges.size() - 1);
			float x = MARGIN_X;
			line(page, x, top, x, bottom);
			for (float w : COL_W) {
				x += w;
				line(page, x, top, x, bottom);
			}
			page.append("S\n");
		}

		private void line(StringBuilder page, float x1, float y1, float x2, float y2) {
			PdfWriter.number(page, x1);
			page.append(' ');
			PdfWriter.number(page, y1);
			page.append(" m ");
			PdfWriter.number(page, x2);
			page.append(' ');
			PdfWriter.number(page, y2);
			page.append(" l\n");
		}

		void titleBlock(Ror1BHeader h) {
			y += paint(text.lines(TITLE_1, false, 13f, CONTENT_W), MARGIN_X, y, CONTENT_W, Align.CENTER);
			y += paint(text.lines(TITLE_2, false, 13f, CONTENT_W), MARGIN_X, y, CONTENT_W, Align.CENTER);
			y += 12f;
			y += paint(text.lines("తేదీ : " + h.date(), true, 10f, CONTENT_W), MARGIN_X, y, CONTENT_W, Align.RIGHT);
			y += 14f;
			infoRow("జిల్లా", h.district(), "మండలము", h.mandal());
			infoRow("గ్రామము", h.village(), "విస్తీర్ణము యూనిట్లు", AREA_UNITS);
			infoRow("ఫసిల్", Integer.toString(h.fasli()), "సంవత్సరం", h.year());
			y += 8f;
		}

		private void infoRow(String l1, String v1, String l2, String v2) {
			float xRight = MARGIN_X + 255f;
			float h = 0;
			h = Math.max(h, paint(text.lines(l1, true, 10f, 65f), MARGIN_X, y, 65f, Align.LEFT));
			h = Math.max(h, paint(text.lines(": " + v1, false, 10f, 175f), MARGIN_X + 70f, y, 175f, Align.LEFT));
			h = Math.max(h, paint(text.lines(l2, true, 10f, 110f), xRight, y, 110f, Align.LEFT));
			h = Math.max(h, paint(text.lines(": " + v2, false, 10f, CONTENT_W - 255f - 115f), xRight + 115f, y,
					CONTENT_W - 255f - 115f, Align.LEFT));
			y += h + 5f;
		}

		void tableHeader() {
			rowEdges.clear();
			rowEdges.add(y);
			draw(prepare(HEADERS, true), alignments(Align.LEFT, Align.LEFT));
			draw(prepare(NUMBERS, true), alignments(Align.CENTER, Align.CENTER));
		}

		void bodyRow(String[] values) throws IOException {
			Row row = prepare(values, false);
			if (y + row.height > PAGE_H - MARGIN_BOTTOM && y > MARGIN_TOP + 1f) {
				newPage();
				tableHeader();
			}
			draw(row, alignments(Align.CENTER, Align.LEFT));
		}

		private Align[] alignments(Align first, Align others) {
			Align[] a = new Align[COL_W.length];
			Arrays.fill(a, others);
			a[0] = first;
			return a;
		}

		private record Row(List<List<TeluguText.Line>> cells, float height) {
		}

		private Row prepare(String[] values, boolean bold) {
			List<List<TeluguText.Line>> cells = new ArrayList<>(COL_W.length);
			float tallest = BODY_SIZE;
			for (int i = 0; i < COL_W.length; i++) {
				List<TeluguText.Line> lines = text.lines(values[i], bold, BODY_SIZE, COL_W[i] - 2 * PAD_X);
				float h = 0;
				for (TeluguText.Line line : lines) {
					h += line.height();
				}
				tallest = Math.max(tallest, h);
				cells.add(lines);
			}
			return new Row(cells, tallest + 2 * PAD_Y);
		}

		private void draw(Row row, Align[] align) {
			float x = MARGIN_X;
			for (int i = 0; i < COL_W.length; i++) {
				paint(row.cells.get(i), x + PAD_X, y + PAD_Y, COL_W[i] - 2 * PAD_X, align[i]);
				x += COL_W[i];
			}
			y += row.height;
			rowEdges.add(y);
		}

		/**
		 * A line drawn this many times in one document becomes a shared form (names,
		 * land types, ...).
		 */
		private static final int PROMOTE_AT = 3;
		private static final int MAX_TRACKED_LINES = 20_000;

		/**
		 * Name of the shared form for a line that has been drawn often enough, else
		 * null (draw it inline).
		 */
		private String lineFormFor(TeluguText.Line line) {
			int[] n = uses.computeIfAbsent(line, k -> new int[1]);
			n[0]++;
			if (n[0] < PROMOTE_AT) {
				return null;
			}
			return lineFormNames.computeIfAbsent(line, l -> {
				String name = "S" + lineFormNames.size();
				lineForms.put(name, new PdfWriter.LineForm(l.draw(), Math.round(l.advance())));
				return name;
			});
		}

		/** Draws shaped lines starting at {@code top}; returns the height used. */
		private float paint(List<TeluguText.Line> lines, float x, float top, float width, Align align) {
			float yy = top;
			for (TeluguText.Line line : lines) {
				float dx = switch (align) {
				case LEFT -> 0f;
				case CENTER -> (width - line.advance()) / 2f;
				case RIGHT -> width - line.advance();
				};
				float originX = x + dx;
				float baseline = yy + line.ascent();
				long x10 = Math.round(originX * 10);
				long y10 = Math.round(baseline * 10);

				// Visible layer: the correctly shaped glyphs as vector shapes, positioned with
				// relative moves.
				if (!line.draw().isEmpty()) {
					String formName = line.chained() ? lineFormFor(line) : null;
					if (formName != null) {
						visible.append("1 0 0 1 ");
						TeluguText.tenths(visible, x10 - chainX);
						visible.append(' ');
						TeluguText.tenths(visible, y10 - chainY);
						visible.append(" cm /").append(formName).append(" Do\n");
						chainX = x10;
						chainY = y10;
					} else if (line.chained()) {
						visible.append("1 0 0 1 ");
						TeluguText.tenths(visible, x10 - chainX);
						visible.append(' ');
						TeluguText.tenths(visible, y10 - chainY);
						visible.append(" cm\n").append(line.draw());
						chainX = x10 + line.endX10();
						chainY = y10 + line.endY10();
					} else {
						visible.append("q 1 0 0 1 ");
						TeluguText.tenths(visible, x10 - chainX);
						visible.append(' ');
						TeluguText.tenths(visible, y10 - chainY);
						visible.append(" cm\n").append(line.draw()).append("Q\n");
					}
					usedForms.addAll(line.forms());
				}
				// Invisible layer: the real Unicode text (render mode 3) - what select / copy /
				// search use.
				if (!line.textRuns().isEmpty()) {
					if (line.size() != textSize) {
						invisible.append('/').append(PdfWriter.textFontResource()).append(' ');
						PdfWriter.number(invisible, line.size());
						invisible.append(" Tf\n");
						textSize = line.size();
					}
					invisible.append("1 0 0 1 ");
					PdfWriter.number(invisible, originX);
					invisible.append(' ');
					PdfWriter.number(invisible, PAGE_H - baseline);
					invisible.append(" Tm\n").append(line.textRuns());
				}
				yy += line.height();
			}
			return yy - top;
		}
	}
}
