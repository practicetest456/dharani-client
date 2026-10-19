package com.example.dharani.ror1b.generate.pdf.standard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.example.dharani.common.util.CommonUtils;

/**
 * GET /api/ror/pdf -> 1-B PDF with every row of ror-data.json GET
 * /api/ror/pdf?khataNumber=1333 -> only that khata GET
 * /api/ror/pdf?surveyNo=1/1/2 -> only that survey number GET
 * /api/ror/pdf?fasli=1436 -> override the automatically calculated fasli year
 * GET /api/ror/pdf?download=true -> download instead of opening in the browser
 *
 * The PDF is streamed page by page, so very large files do not use much memory.
 * Parameter names are written explicitly, so it works even without the
 * "-parameters" compiler flag.
 */
@RestController
@RequestMapping("/api/ror1b/standard-pdf")
public class Ror1bController {

	/** Letters (incl. Telugu), combining marks, digits, '/' and '-'. */
	private static final Pattern SEARCH_TERM = Pattern.compile("^[\\p{L}\\p{M}\\p{N}/\\-]{1,30}$");

	private final RorService service;

	public Ror1bController(RorService service) {
		this.service = service;
	}

	@GetMapping("/pdf")
	public ResponseEntity<StreamingResponseBody> pdf(@RequestParam(name = "fasli", required = false) Integer fasli,
			@RequestParam(name = "khataNumber", required = false) String khataNumber,
			@RequestParam(name = "surveyNo", required = false) String surveyNo,
			@RequestParam(name = "download", defaultValue = "false") boolean download) {

		if (fasli != null && (fasli < 1300 || fasli > 1700)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fasli must be between 1300 and 1700");
		}
		requireValidTerm("khataNumber", khataNumber);
		requireValidTerm("surveyNo", surveyNo);

		List<Ror1bRecord> records = service.search(khataNumber, surveyNo);
		if (records.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No ROR record found for the given search");
		}
		Ror1bRecord firstRecord = records.get(0);
		Map<String, String> details = firstRecord.pattadarDetails();

		String village = CommonUtils.getEnglishName(details.get("Village"));
		String mandal = CommonUtils.getEnglishName(details.get("Mandal"));
		String district = CommonUtils.getEnglishName(details.get("District"));

		String filename = CommonUtils.sanitizeFilename(village) + "_" + CommonUtils.sanitizeFilename(mandal) + "_"
				+ CommonUtils.sanitizeFilename(district) + "_ROR1B_" + CommonUtils.getCurrentDateAsDDMMYYYYHHMMAMPM()
				+ ".pdf";

		ContentDisposition disposition = download
				? ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build()
				: ContentDisposition.inline().filename(filename, StandardCharsets.UTF_8).build();

		StreamingResponseBody body = out -> service.writePdf(records, fasli, out);

		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
				.header("X-Content-Type-Options", "nosniff").body(body);
	}

	/**
	 * API to generate and save the ROR 1-B PDF on the server.
	 *
	 * Examples: GET /api/ror/pdf/save GET /api/ror/pdf/save?khataNumber=1333 GET
	 * /api/ror/pdf/save?surveyNo=1%2F1%2F2 GET /api/ror/pdf/save?fasli=1436
	 *
	 * Returns the saved filename and absolute file path.
	 */
	@GetMapping("/pdf/save")
	public ResponseEntity<Map<String, String>> savePdf(

			// Optional: filter records by khata number.
			@RequestParam(name = "khataNumber", required = false) String khataNumber,

			// Optional: filter records by survey number.
			@RequestParam(name = "surveyNo", required = false) String surveyNo,

			// Optional: override the automatically calculated Fasli year.
			@RequestParam(name = "fasli", required = false) Integer fasli) {

		// Step 1: Validate the Fasli year if provided.
		if (fasli != null && (fasli < 1300 || fasli > 1700)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fasli must be between 1300 and 1700");
		}

		// Step 2: Validate search parameters using your existing method.
		requireValidTerm("khataNumber", khataNumber);
		requireValidTerm("surveyNo", surveyNo);

		// Step 3: Fetch records using your existing service.
		List<Ror1bRecord> records = service.search(khataNumber, surveyNo);

		// Step 4: Return 404 if no matching records exist.
		if (records.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No ROR record found for the given search");
		}

		// Step 5: Generate the PDF and save it to disk.
		try {
			Path savedPath = service.savePdf(records, fasli);

			// Step 6: Return details about the saved file as JSON.
			return ResponseEntity.ok().cacheControl(CacheControl.noStore())
					.body(Map.of("message", "PDF saved successfully", "fileName", savedPath.getFileName().toString(),
							"path", savedPath.toString()));

		} catch (IOException e) {

			// Step 7: Return HTTP 500 if writing the PDF fails.
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save ROR PDF", e);
		}
	}

	private static void requireValidTerm(String name, String value) {
		if (value != null && !value.isBlank() && !SEARCH_TERM.matcher(value.trim()).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " contains invalid characters");
		}
	}
}
