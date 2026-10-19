package com.example.dharani.ror1b.generate.pdf.playwright.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.dharani.common.util.CommonUtils;
import com.example.dharani.ror1b.generate.pdf.playwright.model.RorRecord;
import com.example.dharani.ror1b.generate.pdf.playwright.service.RorPdfService;

/**
 * GET /api/ror1b/playwright-pdf/pdf -> PDF of every row in ror-data.json, GET
 * /api/ror1b/playwright-pdf/pdf?khataNumber=1333 -> only that khata, GET
 * /api/ror1b/playwright-pdf/pdf?surveyNo=1/1/2 -> only that survey no, GET
 * /api/ror1b/playwright-pdf/pdf?fasli=1436 -> override the (auto-calculated)
 * fasli year
 *
 * Parameter names are written explicitly, so this works even when the project
 * is compiled without the "-parameters" flag (e.g. from Eclipse).
 */
@RestController("playwrightRorController")
@RequestMapping("/api/ror1b/playwright-pdf")
public class RorController {

	private final RorPdfService service;

	public RorController(RorPdfService service) {
		this.service = service;
	}

	@GetMapping("/pdf")
	public ResponseEntity<byte[]> pdf(@RequestParam(name = "fasli", required = false) Integer fasli,
			@RequestParam(name = "khataNumber", required = false) String khataNumber,
			@RequestParam(name = "surveyNo", required = false) String surveyNo,
			@RequestParam(name = "download", defaultValue = "false") boolean download) throws Exception {

		List<RorRecord> records = service.search(khataNumber, surveyNo);
		if (records.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		RorRecord firstRecord = records.get(0);
		Map<String, String> details = firstRecord.pattadarDetails();

		String village = getTeluguOrEnglishName(details.get("Village"));
		String mandal = getTeluguOrEnglishName(details.get("Mandal"));
		String district = getTeluguOrEnglishName(details.get("District"));

		String filename = sanitizeFilename(village) + "_" + sanitizeFilename(mandal) + "_" + sanitizeFilename(district)
				+ "_ROR1B_" + CommonUtils.getCurrentDateAsDDMMYYYYHHMMAMPM() + ".pdf";
		byte[] pdf = service.generate(records, fasli);

		ContentDisposition disposition = download
				? ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build()
				: ContentDisposition.inline().filename(filename, StandardCharsets.UTF_8).build();

		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
				.header("X-Content-Type-Options", "nosniff").body(pdf);

	}

	/**
	 * GET /api/ror1b/pdf/save
	 *
	 * Generates the PDF and saves it in the generated-pdfs folder.
	 *
	 * Optional parameters: ?fasli=1436 ?khataNumber=1333 ?surveyNo=1/1/1
	 */
	@GetMapping("/pdf/save")
	public ResponseEntity<String> savePdf(@RequestParam(name = "fasli", required = false) Integer fasli,
			@RequestParam(name = "khataNumber", required = false) String khataNumber,
			@RequestParam(name = "surveyNo", required = false) String surveyNo) throws Exception {

		// Load all records or filter them using the supplied parameters.
		List<RorRecord> records = service.search(khataNumber, surveyNo);

		// Return HTTP 404 if no matching records are found.
		if (records.isEmpty()) {
			return ResponseEntity.notFound().build();
		}

		// Generate the PDF and save it to the output folder.
		Path savedFile = service.savePdf(records, fasli);

		// Tell the API caller where the file was saved.
		return ResponseEntity.ok("PDF saved successfully: " + savedFile);
	}

	private String getTeluguOrEnglishName(String value) {
		if (value == null || value.isBlank()) {
			return "Unknown";
		}

		// "Mittapalle | మిట్టపల్లి" -> "Mittapalle"
		int separator = value.indexOf('|');

		return (separator >= 0 ? value.substring(0, separator) : value).trim();
	}

	private String sanitizeFilename(String value) {
		return value.replaceAll("[\\\\/:*?\"<>|\\s]+", "");
	}
}
