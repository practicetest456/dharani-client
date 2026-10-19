package com.example.dharani.common.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

public class CommonUtils {

	private static final ZoneId INDIA_ZONE = ZoneId.of("Asia/Kolkata");

	// Output example: 12 Oct 2026 04:10 PM
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm a",
			Locale.ENGLISH);

	// Output example: 12 Oct 2026
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

	// Output example: 12Oct2026_04-10_PM
	private static final DateTimeFormatter FILE_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("ddMMMyyyy_hh-mm_a",
			Locale.ENGLISH);

	// Output example: 12Oct2026
	private static final DateTimeFormatter FILE_DATE_FORMATTER = DateTimeFormatter.ofPattern("ddMMMyyyy",
			Locale.ENGLISH);

	private static final DateTimeFormatter DATE_UNDERSCORE_FORMATTER = DateTimeFormatter.ofPattern("dd_MM_yyyy",
			Locale.ENGLISH);

	private static final DateTimeFormatter DATE_TIME_FILENAME_FORMATTER = DateTimeFormatter
			.ofPattern("dd_MM_yyyy-hh_mm_a", Locale.ENGLISH);

	// Prevent object creation because this class contains only utility methods.
	private CommonUtils() {
	}

	/**
	 * Returns the current Indian date in DD_MM_YYYY format.
	 *
	 * Output example: 12_10_2026
	 */
	public static String getCurrentDateAsDDMMYYYY() {
		return ZonedDateTime.now(INDIA_ZONE).format(DATE_UNDERSCORE_FORMATTER);
	}

	/**
	 * Returns the current Indian date and time in dd_MM_yyyy-hh_mm_a here (a=AM/PM)
	 * format.
	 *
	 * Output example: 12_10_2026-04_10_PM
	 */
	public static String getCurrentDateAsDDMMYYYYHHMMAMPM() {
		return ZonedDateTime.now(INDIA_ZONE).format(DATE_TIME_FILENAME_FORMATTER);
	}

	/**
	 * Returns the current Indian date and time.
	 *
	 * Output example: 12 Oct 2026 04:10 PM
	 */
	public static String getCurrentIndianDateTime() {

		return ZonedDateTime.now(INDIA_ZONE).format(DATE_TIME_FORMATTER);
	}

	/**
	 * Returns the current Indian date without time.
	 *
	 * Output example: 12 Oct 2026
	 */
	public static String getCurrentIndianDate() {

		return ZonedDateTime.now(INDIA_ZONE).format(DATE_FORMATTER);
	}

	/**
	 * Returns the current Indian date and time in a filename-friendly format.
	 *
	 * Output example: 12Oct2026_04-10_PM
	 */
	public static String getCurrentIndianFileDateTime() {

		return ZonedDateTime.now(INDIA_ZONE).format(FILE_DATE_TIME_FORMATTER);
	}

	/**
	 * Returns the current Indian date in a filename-friendly format.
	 *
	 * Output example: 12Oct2026
	 */
	public static String getCurrentIndianFileDate() {

		return ZonedDateTime.now(INDIA_ZONE).format(FILE_DATE_FORMATTER);
	}

	/**
	 * Removes spaces and characters that are not suitable for filenames.
	 *
	 * Input : "Siddipet Urban / Telangana"
	 * 
	 * Output: "SiddipetUrbanTelangana"
	 */
	public static String sanitizeFilename(String value) {

		// Use a fallback when the value is missing.
		if (value == null || value.isBlank()) {
			return "Unknown";
		}

		// Remove spaces and characters that are not suitable for filenames.
		return value.trim().replaceAll("[\\\\/:*?\"<>|\\s]+", "");
	}

	/**
	 * Extracts the English name from an English | Telugu value.
	 *
	 * Input : "Mittapalle | మిట్టపల్లి" Output: "Mittapalle"
	 */
	public static String getEnglishName(String value) {

		// Use a fallback when the value is missing.
		if (value == null || value.isBlank()) {
			return "Unknown";
		}

		// Find the separator between English and Telugu.
		int separator = value.indexOf('|');

		// Return the English name, or the whole value if no separator exists.
		return (separator >= 0 ? value.substring(0, separator) : value).trim();
	}

	/**
	 * Extracts the Telugu name from an English | Telugu value.
	 *
	 * Input : "Mittapalle | మిట్టపల్లి" Output: "మిట్టపల్లి"
	 */
	public static String getTeluguName(String value) {

		// Use a fallback when the value is missing.
		if (value == null || value.isBlank()) {
			return "Unknown";
		}

		// Find the separator between English and Telugu.
		int separator = value.lastIndexOf('|');

		// Return the Telugu name, or the whole value if no separator exists.
		return (separator >= 0 ? value.substring(separator + 1) : value).trim();
	}

	/**
	 * Generates an ROR 1B PDF filename using location details and the current
	 * Indian date and time.
	 *
	 * @param village  the village name
	 * @param mandal   the mandal name
	 * @param district the district name
	 * @return the generated ROR 1B PDF filename
	 * 
	 *         Example: Mittapalle_Siddipet(Urban)_Siddipet_ROR1B_12_10_2026.pdf
	 */
	public static String generateRor1bPdfFilename(String village, String mandal, String district) {

		// Sanitize location names before using them in the filename.
		String cleanVillage = sanitizeFilename(village);
		String cleanMandal = sanitizeFilename(mandal);
		String cleanDistrict = sanitizeFilename(district);

		// Build the filename using location details and the current timestamp.
		return cleanVillage + "_" + cleanMandal + "_" + cleanDistrict + "_ROR1B_" + getCurrentDateAsDDMMYYYY() + ".pdf";
	}

	/**
	 * Generates an ROR 1B PDF filename using location details and the current
	 * Indian date and time.
	 *
	 * @param village  the village name
	 * @param mandal   the mandal name
	 * @param district the district name
	 * @return the generated ROR 1B PDF filename
	 * 
	 *         Example:
	 *         Mittapalle_Siddipet(Urban)_Siddipet_ROR1B_12_10_2026-04_10_PM.pdf
	 */
	public static String generateRor1bPdfFilenameWithTime(String village, String mandal, String district) {

		// Sanitize location names before using them in the filename.
		String cleanVillage = sanitizeFilename(village);
		String cleanMandal = sanitizeFilename(mandal);
		String cleanDistrict = sanitizeFilename(district);

		// Build the filename using location details and the current timestamp.
		return cleanVillage + "_" + cleanMandal + "_" + cleanDistrict + "_ROR1B_" + getCurrentDateAsDDMMYYYYHHMMAMPM()
				+ ".pdf";
	}

	/**
	 * Generates an ROR 1B PDF filename using a numeric date and time.
	 *
	 * @param village  the village name
	 * @param mandal   the mandal name
	 * @param district the district name
	 * @return the generated ROR 1B PDF filename
	 * 
	 *         Example: Village_Mandal_District_ROR1B_12Oct2026.pdf
	 */
	public static String generateRor1bPdfFilenameWithFormattedDate(String village, String mandal, String district) {

		String cleanVillage = sanitizeFilename(village);
		String cleanMandal = sanitizeFilename(mandal);
		String cleanDistrict = sanitizeFilename(district);

		return cleanVillage + "_" + cleanMandal + "_" + cleanDistrict + "_ROR1B_" + getCurrentIndianFileDate() + ".pdf";
	}

	/**
	 * Generates an ROR 1B PDF filename using a formatted date and time.
	 * 
	 * @param village  the village name
	 * @param mandal   the mandal name
	 * @param district the district name
	 * @return the generated ROR 1B PDF filename
	 * 
	 *         Example: Village_Mandal_District_ROR1B_12Oct2026_04-10_PM.pdf
	 */
	public static String generateRor1bPdfFilenameWithFormattedDateTime(String village, String mandal, String district) {

		String cleanVillage = sanitizeFilename(village);
		String cleanMandal = sanitizeFilename(mandal);
		String cleanDistrict = sanitizeFilename(district);

		return cleanVillage + "_" + cleanMandal + "_" + cleanDistrict + "_ROR1B_" + getCurrentIndianFileDateTime()
				+ ".pdf";
	}

	public static Path generate_RoR_1B_PdfPath(Map<String, String> details, String storageLocation) throws IOException {

		if (details == null) {
			throw new IllegalArgumentException("Location details must not be null");
		}

		String village = getEnglishName(details.get("Village"));
		String mandal = getEnglishName(details.get("Mandal"));
		String district = getEnglishName(details.get("District"));

		// Step 1: Sanitize the names before using them in paths.
		String safeVillage = sanitizeFilename(village);
		String safeMandal = sanitizeFilename(mandal);
		String safeDistrict = sanitizeFilename(district);

		// Step 2: Build the PDF filename dynamically.
		String filename = safeVillage + "_" + safeMandal + "_" + safeDistrict + "_ROR1B_" + getCurrentDateAsDDMMYYYYHHMMAMPM()
				+ ".pdf";

		// Step 3: Define the base folder where PDFs will be saved.
		Path baseDirectory = Paths.get(storageLocation).toAbsolutePath().normalize();

		// Step 4: Create district and mandal subfolders.
		Path districtDirectory = baseDirectory.resolve(safeDistrict);

		Path mandalDirectory = districtDirectory.resolve(safeMandal);

		// Create all missing folders automatically.
		Files.createDirectories(mandalDirectory);

		// Step 5: Create the final output file path.
		Path outputFile = mandalDirectory.resolve(filename);

		return outputFile;

	}
}