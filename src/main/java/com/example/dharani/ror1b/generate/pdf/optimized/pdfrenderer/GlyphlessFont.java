package com.example.dharani.ror1b.generate.pdf.optimized.pdfrenderer;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.TreeMap;

/**
 * A tiny generated TrueType font (a few hundred bytes) that carries the
 * invisible text layer.
 *
 * Glyph 0 is empty. Glyph i (i &gt;= 1) is a plain box exactly as wide as the
 * i-th advance width requested and one em high. The text is drawn invisibly
 * (render mode 3), so the boxes are never seen; they exist because PDF viewers
 * derive the character boxes used for selection and search highlights from the
 * glyph box: with a box as wide as the character's advance, highlights cover
 * the full width and height of every character.
 */
final class GlyphlessFont {

	static final int ASCENT = 800;
	static final int DESCENT = -200;
	private static final int BOX_BYTES = 36;

	private GlyphlessFont() {
	}

	/** @param advances advance width (1/1000 em) of glyph 1, 2, 3, ... */
	static byte[] create(int[] advances) {
		int glyphs = advances.length + 1;
		int maxAdvance = 0;
		for (int a : advances) {
			maxAdvance = Math.max(maxAdvance, a);
		}

		Map<String, byte[]> tables = new TreeMap<>();
		tables.put("cmap", cmap());
		tables.put("glyf", glyf(advances));
		tables.put("head", head(maxAdvance));
		tables.put("hhea", hhea(glyphs, maxAdvance));
		tables.put("hmtx", hmtx(advances));
		tables.put("loca", loca(glyphs));
		tables.put("maxp", maxp(glyphs));
		tables.put("name", new byte[] { 0, 0, 0, 0, 0, 6, 0, 0 });
		tables.put("post", new byte[] { 0, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0, 0 });

		int n = tables.size();
		int offset = 12 + 16 * n;
		ByteBuffer dir = ByteBuffer.allocate(offset);
		dir.putInt(0x00010000).putShort((short) n);
		int pow = Integer.highestOneBit(n);
		dir.putShort((short) (pow * 16)).putShort((short) Integer.numberOfTrailingZeros(pow))
				.putShort((short) (n * 16 - pow * 16));
		int total = offset;
		for (byte[] t : tables.values()) {
			total += (t.length + 3) & ~3;
		}
		ByteBuffer font = ByteBuffer.allocate(total);
		font.position(offset);
		for (Map.Entry<String, byte[]> e : tables.entrySet()) {
			byte[] t = e.getValue();
			int start = font.position();
			font.put(t);
			while (font.position() % 4 != 0) {
				font.put((byte) 0);
			}
			for (char c : e.getKey().toCharArray()) {
				dir.put((byte) c);
			}
			dir.putInt(checksum(t)).putInt(start).putInt(t.length);
		}
		font.position(0);
		font.put(dir.array());
		return font.array();
	}

	/**
	 * One rectangle contour per glyph: (0,descent) (w,descent) (w,ascent)
	 * (0,ascent).
	 */
	private static byte[] glyf(int[] advances) {
		ByteBuffer b = ByteBuffer.allocate(Math.max(4, advances.length * BOX_BYTES));
		for (int w : advances) {
			int start = b.position();
			b.putShort((short) 1);
			b.putShort((short) 0).putShort((short) DESCENT).putShort((short) w).putShort((short) ASCENT);
			b.putShort((short) 3).putShort((short) 0);
			b.put(new byte[] { 1, 1, 1, 1 });
			b.putShort((short) 0).putShort((short) w).putShort((short) 0).putShort((short) -w);
			b.putShort((short) DESCENT).putShort((short) 0).putShort((short) (ASCENT - DESCENT)).putShort((short) 0);
			b.position(start + BOX_BYTES);
		}
		return b.array();
	}

	/**
	 * Short offsets (stored /2): glyph 0 is empty, glyph i occupies BOX_BYTES at
	 * (i-1)*BOX_BYTES.
	 */
	private static byte[] loca(int glyphs) {
		ByteBuffer b = ByteBuffer.allocate(((glyphs + 1) * 2 + 3) & ~3);
		b.putShort((short) 0);
		for (int g = 1; g <= glyphs; g++) {
			b.putShort((short) ((g - 1) * BOX_BYTES / 2));
		}
		return b.array();
	}

	private static byte[] hmtx(int[] advances) {
		ByteBuffer b = ByteBuffer.allocate((advances.length + 1) * 4);
		b.putShort((short) 0).putShort((short) 0);
		for (int w : advances) {
			b.putShort((short) w).putShort((short) 0);
		}
		return b.array();
	}

	private static byte[] head(int maxAdvance) {
		ByteBuffer b = ByteBuffer.allocate(54);
		b.putInt(0x00010000).putInt(0x00010000).putInt(0).putInt(0x5F0F3CF5);
		b.putShort((short) 0).putShort((short) 1000);
		b.putLong(0).putLong(0);
		b.putShort((short) 0).putShort((short) DESCENT).putShort((short) maxAdvance).putShort((short) ASCENT);
		b.putShort((short) 0).putShort((short) 8).putShort((short) 2).putShort((short) 0).putShort((short) 0);
		return b.array();
	}

	private static byte[] hhea(int glyphs, int maxAdvance) {
		ByteBuffer b = ByteBuffer.allocate(36);
		b.putInt(0x00010000).putShort((short) ASCENT).putShort((short) DESCENT).putShort((short) 0);
		b.putShort((short) maxAdvance).putShort((short) 0).putShort((short) 0).putShort((short) maxAdvance);
		b.putShort((short) 1).putShort((short) 0).putShort((short) 0);
		b.position(32);
		b.putShort((short) 0).putShort((short) glyphs); // metricDataFormat, numberOfHMetrics
		return b.array();
	}

	private static byte[] maxp(int glyphs) {
		ByteBuffer b = ByteBuffer.allocate(32);
		b.putInt(0x00010000).putShort((short) glyphs).putShort((short) 4).putShort((short) 1);
		b.position(14);
		b.putShort((short) 1); // maxZones
		return b.array();
	}

	/** Format 4 cmap that maps nothing (only the mandatory terminating segment). */
	private static byte[] cmap() {
		ByteBuffer b = ByteBuffer.allocate(36);
		b.putShort((short) 0).putShort((short) 1).putShort((short) 3).putShort((short) 1).putInt(12);
		b.putShort((short) 4).putShort((short) 24).putShort((short) 0).putShort((short) 2).putShort((short) 2)
				.putShort((short) 0).putShort((short) 0);
		b.putShort((short) 0xFFFF).putShort((short) 0).putShort((short) 0xFFFF).putShort((short) 1).putShort((short) 0);
		return b.array();
	}

	private static int checksum(byte[] data) {
		ByteBuffer b = ByteBuffer.allocate((data.length + 3) & ~3).put(data);
		b.flip();
		int sum = 0;
		while (b.remaining() >= 4) {
			sum += b.getInt();
		}
		return sum;
	}
}
