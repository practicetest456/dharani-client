package com.example.dharani.ror1b.generate.pdf.standard.pdfrender;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.dharani.ror1b.generate.pdf.standard.Ror1bRecord;

/**
 * Draws the Telugu "1-B (ROR)" register as a multi-page A4 PDF. Pure Java: no
 * browser, no native libraries, no downloads. Pages are streamed to the output
 * as they fill up, so a very large number of rows needs only a few MB of
 * memory.
 */
@Component
public class Ror1BPdfRenderer {

	private static final String TITLE_1 = "తెలంగాణ ప్రభుత్వము, రెవెన్యూశాఖ, భూమి రికార్డుల కంప్యూటీకరణ";
	private static final String TITLE_2 = "1-బి నమూనా(ROR)";
	private static final String AREA_UNITS = "ఏ./గుం.";
	private static final String[] HEADERS = { "వరుస నెం.", "పట్టాదారు పేరు", "(తండ్రి/ భర్త పేరు)", "ఖాతా నెంబరు",
			"సర్వే నెంబర్", "భూమి వివరణ", "మొత్తం విస్తీర్ణము" };
	private static final String[] NUMBERS = { "(1)", "(2)", "(3)", "(4)", "(5)", "(6)", "(7)" };
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
	private static final float[] COL_W = { 38f, 125f, 115f, 52f, 62f, 65f, 58.28f };
	private static final float PAD_X = 4f;
	private static final float PAD_Y = 4f;
	private static final float BODY_SIZE = 9f;

	private enum Align {
		LEFT, CENTER, RIGHT
	}

	private final TeluguText text = new TeluguText(); // loads the bundled fonts once, fails fast

	/** Writes the PDF to {@code out} (flushed, not closed). */
	public void render(Ror1BHeader header, List<Ror1bRecord> rows, OutputStream out) throws IOException {
		PdfWriter pdf = new PdfWriter(new BufferedOutputStream(out, 64 * 1024), text.textFont());
		Sheet sheet = new Sheet(pdf);
		sheet.newPage();
		sheet.titleBlock(header);
		sheet.tableHeader();
		int n = 1;
		for (Ror1bRecord r : rows) {
			sheet.bodyRow(cells(n++, r));
		}
		sheet.close();
	}

	private static String[] cells(int serial, Ror1bRecord r) {
		Map<String, String> p = r.pattadarDetails();
		Map<String, String> l = r.landDetails();
		String nature = l.getOrDefault("Classification of Land", "");
		return new String[] { Integer.toString(serial), beforeSlash(p.get("Pattadar / Authorised Person Name")),
				beforeSlash(p.get("Father / Husband's Name")), nullToEmpty(r.khataNumber()), nullToEmpty(r.surveyNo()),
				LAND_DESCRIPTION.getOrDefault(nature, nature), nullToEmpty(l.get("Total Extent (Ac. Gts)")) };
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
		private StringBuilder page;
		private float y;
		private final java.util.Set<String> usedForms = new java.util.LinkedHashSet<>();

		Sheet(PdfWriter pdf) {
			this.pdf = pdf;
		}

		void newPage() throws IOException {
			flushPage();
			page = new StringBuilder(48 * 1024);
			// Flip to a top-left origin so all coordinates below read like screen
			// coordinates.
			page.append("1 0 0 -1 0 ");
			PdfWriter.number(page, PAGE_H);
			page.append(" cm\n0 g 0.25 G 0.5 w\n");
			y = MARGIN_TOP;
		}

		void close() throws IOException {
			flushPage();
			java.util.Map<String, String> forms = new java.util.LinkedHashMap<>();
			for (String name : usedForms) {
				forms.put(name, text.glyphForm(name));
			}
			pdf.finish("ROR 1-B", forms);
		}

		private void flushPage() throws IOException {
			if (page != null) {
				pdf.addPage(PAGE_W, PAGE_H, page);
				page = null;
			}
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
			java.util.Arrays.fill(a, others);
			a[0] = first;
			return a;
		}

		private record Row(List<List<TeluguText.Line>> cells, float height) {
		}

		private Row prepare(String[] values, boolean bold) {
			List<List<TeluguText.Line>> cells = new java.util.ArrayList<>(COL_W.length);
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
				PdfWriter.number(page, x);
				page.append(' ');
				PdfWriter.number(page, y);
				page.append(' ');
				PdfWriter.number(page, COL_W[i]);
				page.append(' ');
				PdfWriter.number(page, row.height);
				page.append(" re S\n");
				paint(row.cells.get(i), x + PAD_X, y + PAD_Y, COL_W[i] - 2 * PAD_X, align[i]);
				x += COL_W[i];
			}
			y += row.height;
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

				// Visible layer: the correctly shaped glyphs as vector shapes.
				if (!line.draw().isEmpty()) {
					page.append("q 1 0 0 1 ");
					PdfWriter.number(page, originX);
					page.append(' ');
					PdfWriter.number(page, baseline);
					page.append(" cm\n").append(line.draw()).append("Q\n");
					usedForms.addAll(line.forms());
				}
				// Invisible layer: the real Unicode text (render mode 3) - this is what select
				// / copy / search use.
				if (!line.textRuns().isEmpty()) {
					page.append("q BT 3 Tr 1 0 0 -1 ");
					PdfWriter.number(page, originX);
					page.append(' ');
					PdfWriter.number(page, baseline);
					page.append(" Tm /").append(PdfWriter.textFontResource()).append(' ');
					PdfWriter.number(page, line.size());
					page.append(" Tf\n").append(line.textRuns()).append("ET Q\n");
				}
				yy += line.height();
			}
			return yy - top;
		}
	}
}