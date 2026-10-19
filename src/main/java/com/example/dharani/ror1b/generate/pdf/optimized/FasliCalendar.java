package com.example.dharani.ror1b.generate.pdf.optimized;

import java.time.LocalDate;

/**
 * Fasli year = Gregorian start year - 590; it runs 1 July to 30 June (2026-27
 * -> 2026 - 590 = 1436).
 */
public final class FasliCalendar {

	private static final int OFFSET = 590;

	private FasliCalendar() {
	}

	public static int currentFasli(LocalDate today) {
		int startYear = today.getMonthValue() >= 7 ? today.getYear() : today.getYear() - 1;
		return startYear - OFFSET;
	}

	/** 1436 -> "2026-27" */
	public static String yearLabel(int fasli) {
		int start = fasli + OFFSET;
		return "%d-%02d".formatted(start, (start + 1) % 100);
	}
}
