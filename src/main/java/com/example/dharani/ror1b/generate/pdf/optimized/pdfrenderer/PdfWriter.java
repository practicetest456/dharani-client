package com.example.dharani.ror1b.generate.pdf.optimized.pdfrenderer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Minimal streaming PDF 1.4 writer. Pages are written as soon as they are
 * complete, so memory stays flat however many pages the document has.
 *
 * A page holds two layers: the visible Telugu drawn as vector glyph shapes
 * (form XObjects, defined once each), and an invisible text layer (render mode
 * 3) in an embedded Unicode-coded font, which is what PDF viewers select, copy,
 * search and highlight.
 */
final class PdfWriter {

	/**
	 * The font of the invisible text layer. Codes are UTF-16 code units;
	 * {@code cidToGid} maps code -> glyph (2 bytes each) so viewers get real glyph
	 * boxes; {@code widths} is the /W entry (advance per code).
	 */
	record TextFont(String baseName, byte[] packedTtf, int rawLength, int[] bbox, byte[] cidToGid, String widths) {
	}

	private static final int CATALOG_ID = 1;
	private static final int PAGES_ID = 2;
	private static final int RESOURCES_ID = 3;
	private static final int GLYPH_RESOURCES_ID = 4;
	private static final String FONT_RESOURCE = "Fu";

	private static final String IDENTITY_TO_UNICODE = "/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n"
			+ "/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n"
			+ "/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n"
			+ "1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange\n"
			+ "1 beginbfrange\n<0000> <FFFF> <0000>\nendbfrange\n"
			+ "endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n";

	private final OutputStream out;
	private final List<Long> offsets = new ArrayList<>(); // index = objectId - 1
	private final List<Integer> pageIds = new ArrayList<>();
	private final int textFontId;
	private long position;

	PdfWriter(OutputStream out, TextFont font) throws IOException {
		this.out = out;
		offsets.add(-1L); // 1 catalog (written last)
		offsets.add(-1L); // 2 pages (written last)
		offsets.add(-1L); // 3 resources (written last, once all glyph forms are known)
		offsets.add(-1L); // 4 resources of the line forms (glyph shapes only)
		write(new byte[] { '%', 'P', 'D', 'F', '-', '1', '.', '4', '\n', '%', (byte) 0xE2, (byte) 0xE3, (byte) 0xCF,
				(byte) 0xD3, '\n' });
		this.textFontId = embed(font);
	}

	private int embed(TextFont f) throws IOException {
		int fileId = newObject();
		begin(fileId);
		ascii("<< /Length " + f.packedTtf().length + " /Length1 " + f.rawLength()
				+ " /Filter /FlateDecode >>\nstream\n");
		write(f.packedTtf());
		ascii("\nendstream\nendobj\n");

		int descId = newObject();
		begin(descId);
		int[] b = f.bbox();
		ascii("<< /Type /FontDescriptor /FontName /" + f.baseName() + " /Flags 4 /FontBBox [" + b[0] + " " + b[1] + " "
				+ b[2] + " " + b[3] + "] /ItalicAngle 0 /Ascent " + b[3] + " /Descent " + b[1]
				+ " /CapHeight 700 /StemV 80 /FontFile2 " + fileId + " 0 R >>\nendobj\n");

		int mapId = newStream(f.cidToGid(), "");

		int cidId = newObject();
		begin(cidId);
		ascii("<< /Type /Font /Subtype /CIDFontType2 /BaseFont /" + f.baseName()
				+ " /CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >>" + " /FontDescriptor "
				+ descId + " 0 R /DW 600 " + f.widths() + " /CIDToGIDMap " + mapId + " 0 R >>\nendobj\n");

		int toUnicodeId = newStream(IDENTITY_TO_UNICODE.getBytes(StandardCharsets.US_ASCII), "");

		int fontId = newObject();
		begin(fontId);
		ascii("<< /Type /Font /Subtype /Type0 /BaseFont /" + f.baseName() + " /Encoding /Identity-H /DescendantFonts [ "
				+ cidId + " 0 R ] /ToUnicode " + toUnicodeId + " 0 R >>\nendobj\n");
		return fontId;
	}

	/** Adds one page; {@code content} is the (ASCII) page content stream. */
	void addPage(float width, float height, CharSequence content) throws IOException {
		int contentId = newStream(content.toString().getBytes(StandardCharsets.US_ASCII), "");

		int pageId = newObject();
		begin(pageId);
		StringBuilder sb = new StringBuilder(200).append("<< /Type /Page /Parent ").append(PAGES_ID)
				.append(" 0 R /MediaBox [0 0 ");
		number(sb, width);
		sb.append(' ');
		number(sb, height);
		sb.append("] /Resources ").append(RESOURCES_ID).append(" 0 R /Contents ").append(contentId)
				.append(" 0 R >>\nendobj\n");
		ascii(sb.toString());
		pageIds.add(pageId);
	}

	/**
	 * Writes the glyph shapes, resources, page tree, catalog, info, xref and
	 * trailer. The stream is flushed, not closed.
	 *
	 * @param glyphForms form name -> path operators of every glyph shape used in
	 *                   the document
	 * @param lineForms  form name -> reusable whole lines (repeated cell values)
	 */
	/**
	 * A whole drawn line (glyph chain) that is reused: the content is written once,
	 * every use is one short operator.
	 */
	record LineForm(String content, int width) {
	}

	void finish(String title, Map<String, String> glyphForms, Map<String, LineForm> lineForms) throws IOException {
		if (pageIds.isEmpty()) {
			throw new IllegalStateException("A PDF needs at least one page");
		}
		StringBuilder xobjects = new StringBuilder();
		for (Map.Entry<String, String> form : glyphForms.entrySet()) {
			int id = newStream(form.getValue().getBytes(StandardCharsets.US_ASCII),
					"/Type /XObject /Subtype /Form /BBox [-100 -100 100 100] ");
			xobjects.append(" /").append(form.getKey()).append(' ').append(id).append(" 0 R");
		}
		begin(GLYPH_RESOURCES_ID);
		ascii("<< /XObject <<" + xobjects + " >> >>\nendobj\n");
		StringBuilder all = new StringBuilder(xobjects);
		for (Map.Entry<String, LineForm> form : lineForms.entrySet()) {
			int id = newStream(form.getValue().content().getBytes(StandardCharsets.US_ASCII),
					"/Type /XObject /Subtype /Form /BBox [-30 -40 " + (form.getValue().width() + 60)
							+ " 40] /Resources " + GLYPH_RESOURCES_ID + " 0 R ");
			all.append(" /").append(form.getKey()).append(' ').append(id).append(" 0 R");
		}
		begin(RESOURCES_ID);
		ascii("<< /Font << /" + FONT_RESOURCE + " " + textFontId + " 0 R >> /XObject <<" + all + " >> >>\nendobj\n");

		begin(PAGES_ID);
		StringBuilder kids = new StringBuilder("<< /Type /Pages /Count ").append(pageIds.size()).append(" /Kids [");
		for (int id : pageIds) {
			kids.append(' ').append(id).append(" 0 R");
		}
		kids.append(" ] >>\nendobj\n");
		ascii(kids.toString());

		begin(CATALOG_ID);
		ascii("<< /Type /Catalog /Pages " + PAGES_ID + " 0 R /Lang (te-IN) >>\nendobj\n");

		int infoId = newObject();
		begin(infoId);
		ascii("<< /Title (" + title.replaceAll("[()\\\\]", "") + ") /Producer (dharani-client) >>\nendobj\n");

		long xrefAt = position;
		StringBuilder x = new StringBuilder("xref\n0 ").append(offsets.size() + 1).append("\n0000000000 65535 f \n");
		for (long off : offsets) {
			x.append(String.format("%010d 00000 n \n", off));
		}
		x.append("trailer\n<< /Size ").append(offsets.size() + 1).append(" /Root ").append(CATALOG_ID)
				.append(" 0 R /Info ").append(infoId).append(" 0 R >>\n").append("startxref\n").append(xrefAt)
				.append("\n%%EOF\n");
		ascii(x.toString());
		out.flush();
	}

	/** Name of the invisible-text font in the page resources. */
	static String textFontResource() {
		return FONT_RESOURCE;
	}

	/** Compact number formatting (max 2 decimals, never scientific notation). */
	static void number(StringBuilder sb, double v) {
		long r = Math.round(v * 100.0);
		if (r < 0) {
			sb.append('-');
			r = -r;
		}
		sb.append(r / 100);
		long frac = r % 100;
		if (frac != 0) {
			sb.append('.');
			if (frac < 10) {
				sb.append('0');
			}
			sb.append(frac);
			if (frac % 10 == 0) {
				sb.setLength(sb.length() - 1);
			}
		}
	}

	static byte[] deflate(byte[] raw) throws IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream(raw.length / 4 + 64);
		Deflater deflater = new Deflater(9);
		try (DeflaterOutputStream z = new DeflaterOutputStream(bos, deflater)) {
			z.write(raw);
		} finally {
			deflater.end();
		}
		return bos.toByteArray();
	}

	/** Writes a deflated stream object and returns its id. */
	private int newStream(byte[] raw, String extraDictEntries) throws IOException {
		byte[] packed = deflate(raw);
		int id = newObject();
		begin(id);
		ascii("<< " + extraDictEntries + "/Length " + packed.length + " /Filter /FlateDecode >>\nstream\n");
		write(packed);
		ascii("\nendstream\nendobj\n");
		return id;
	}

	private int newObject() {
		offsets.add(-1L);
		return offsets.size();
	}

	private void begin(int id) throws IOException {
		offsets.set(id - 1, position);
		ascii(id + " 0 obj\n");
	}

	private void ascii(String s) throws IOException {
		write(s.getBytes(StandardCharsets.US_ASCII));
	}

	private void write(byte[] b) throws IOException {
		out.write(b);
		position += b.length;
	}
}
