package com.example.dharani.ror1b.generate.pdf.optimized;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

import com.example.dharani.common.model.Ror1BLandDetails;
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

@RestController("lessSizePdfRorController")
@RequestMapping("/api/ror1b/optimized-pdf")
public class RorController {

	/** Letters (incl. Telugu), combining marks, digits, '/' and '-'. */
	private static final Pattern SEARCH_TERM = Pattern.compile("^[\\p{L}\\p{M}\\p{N}/\\-]{1,30}$");

	private final RorService service;

	public RorController(RorService service) {
		this.service = service;
	}

	/**
	 * API to generate(from ror-data.json not from server) and send the ROR 1-B PDF on the server.
	 **/
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

		List<Ror1BLandDetails> records = service.search(khataNumber, surveyNo);
		if (records.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No ROR record found for the given search");
		}

		Ror1BLandDetails firstRecord = records.get(0);
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
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.header("X-Content-Type-Options", "nosniff").cacheControl(CacheControl.noStore()) // land records are
																									// personal data
				.body(body);
	}

	/**
	 * API to generate(from ror-data.json not from server) and save the ROR 1-B PDF on the server.
	 *
	 * Examples: GET /api/ror/lessSizePdfCode/pdf/save GET
	 * /api/ror/lessSizePdfCode/pdf/save?khataNumber=1333 GET
	 * /api/ror/lessSizePdfCode/pdf/save?surveyNo=1%2F1%2F2 GET
	 * /api/ror/lessSizePdfCode/pdf/save?fasli=1436
	 *
	 * Returns the saved filename and absolute file path.
	 */
	@GetMapping("/pdf/save")
	public ResponseEntity<Map<String, String>> savePdf(

			// Optional: filter records by Khata number.
			@RequestParam(name = "khataNumber", required = false) String khataNumber,

			// Optional: filter records by survey number.
			@RequestParam(name = "surveyNo", required = false) String surveyNo,

			// Optional: override the automatically calculated Fasli year.
			@RequestParam(name = "fasli", required = false) Integer fasli) {

		// Step 1: Validate the Fasli year if provided.
		if (fasli != null && (fasli < 1300 || fasli > 1700)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fasli must be between 1300 and 1700");
		}

		// Step 2: Validate the search parameters.
		requireValidTerm("khataNumber", khataNumber);
		requireValidTerm("surveyNo", surveyNo);

		// Step 3: Retrieve matching records using the existing service.
		List<Ror1BLandDetails> records = service.search(khataNumber, surveyNo);

		// Step 4: Return 404 when no matching records exist.
		if (records.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No ROR record found for the given search");
		}

		try {
			// Step 5: Generate the PDF and save it to the server.
			Path savedPath = service.generateRor1BPdf(records, fasli);

			// Step 6: Return the saved file details as JSON.
			return ResponseEntity.ok().cacheControl(CacheControl.noStore())
					.body(Map.of("message", "PDF saved successfully", "fileName", savedPath.getFileName().toString(),
							"path", savedPath.toString()));

		} catch (IOException e) {

			// Step 7: Return HTTP 500 if saving or PDF generation fails.
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save ROR PDF", e);
		}
	}

	/**
	 * Fetches the latest ROR-1B land records from the Bhu Bharathi system
	 * based on the provided district, mandal, village, survey number, and
	 * khata numbers, and streams the generated JSON directly to the client.
	 * The PDF can be displayed inline in the browser or downloaded as an
	 * attachment, depending on the download parameter.
	 *
	 * @param districtId   the ID of the district (optional)
	 * @param mandalId     the ID of the mandal (optional)
	 * @param villageId    the ID of the village (optional)
	 * @param surveyNo     the survey number used to filter land records (optional)
	 * @param khataNumbers the set of khata numbers used to filter land records (optional)
	 * @param download     true to download the PDF; false to display it inline
	 * @return a ResponseEntity containing the streamed PDF with appropriate headers
	 * @throws ResponseStatusException if no matching ROR-1B records are found
	 * 
	 * Example: http://localhost:8080/api/ror1b/optimized-pdf/pdf/generate?districtId=10&mandalId=205&villageId=1019006&khataNumbers=346
	 */
	@GetMapping("/generate/json")
	public List<Ror1BLandDetails> generateRor1BJson(
			@RequestParam(name = "districtId", required = false) Integer districtId,
			@RequestParam(name = "mandalId", required = false) Integer mandalId,
			@RequestParam(name = "villageId", required = false) Integer villageId,
			@RequestParam(name = "surveyNo", required = false) String surveyNo,
			@RequestParam(name = "khataNumbers", required = false) Set<String> khataNumbers,
			@RequestParam(name = "download", defaultValue = "false") boolean download) {

		// Step 1: Generate the PDF and save it to the server.
		List<Ror1BLandDetails> records = service.generateRor1BJson(districtId, mandalId, villageId, surveyNo,
				khataNumbers);
		if (records.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No ROR record found for the given search");
		}
		return records;
	}

		
	/**
	 * Fetches the latest ROR-1B land records from the Bhu Bharathi system
	 * based on the provided district, mandal, village, survey number, and
	 * khata numbers, and streams the generated PDF directly to the client.
	 * The PDF can be displayed inline in the browser or downloaded as an
	 * attachment, depending on the download parameter.
	 *
	 * @param districtId   the ID of the district (optional)
	 * @param mandalId     the ID of the mandal (optional)
	 * @param villageId    the ID of the village (optional)
	 * @param surveyNo     the survey number used to filter land records (optional)
	 * @param khataNumbers the set of khata numbers used to filter land records (optional)
	 * @param download     true to download the PDF; false to display it inline
	 * @return a ResponseEntity containing the streamed PDF with appropriate headers
	 * @throws ResponseStatusException if no matching ROR-1B records are found
	 * 
	 * Example: http://localhost:8080/api/ror1b/optimized-pdf/pdf/generate?districtId=10&mandalId=205&villageId=1019006&khataNumbers=346
	 */
	@GetMapping("/generate/pdf")
	public ResponseEntity<StreamingResponseBody> generatePdf(
			@RequestParam(name = "districtId", required = false) Integer districtId,
			@RequestParam(name = "mandalId", required = false) Integer mandalId,
			@RequestParam(name = "villageId", required = false) Integer villageId,
			@RequestParam(name = "surveyNo", required = false) String surveyNo,
			@RequestParam(name = "khataNumbers", required = false) Set<String> khataNumbers,
			@RequestParam(name = "download", defaultValue = "false") boolean download) {

		// Step 1: Generate the PDF and save it to the server.
		List<Ror1BLandDetails> records = service.generateRor1BJson(districtId, mandalId, villageId, surveyNo,
				khataNumbers);
		if (records.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No ROR record found for the given search");
		}

		Ror1BLandDetails firstRecord = records.get(0);
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
		StreamingResponseBody body = out -> service.writePdf(records, null, out);
		return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.header("X-Content-Type-Options", "nosniff").cacheControl(CacheControl.noStore()) // land records are
																									// personal data
				.body(body);
	}
	
	/**
	 * Fetches the latest ROR-1B land records from the Bhu Bharathi system
	 * based on the provided district, mandal, village, survey number, and
	 * khata numbers, then generates and saves the corresponding PDF files.
	 *
	 * @param districtId   the ID of the district (optional)
	 * @param mandalId     the ID of the mandal (optional)
	 * @param villageId    the ID of the village (optional)
	 * @param surveyNo     the survey number used to filter land records (optional)
	 * @param khataNumbers the set of khata numbers used to filter land records (optional)
	 * @return a ResponseEntity containing a success message and PDF file details
	 * 
	 * Example: http://localhost:8080/api/ror1b/optimized-pdf/pdf/generate?districtId=10&mandalId=205&villageId=1019006&khataNumbers=346
	 * 
	 */
	@GetMapping("/generate/pdf/saveAll")
	public ResponseEntity<Map<String, String>> saveMultiplePdfs(
			@RequestParam(name = "districtId", required = false) Integer districtId,
			@RequestParam(name = "mandalId", required = false) Integer mandalId,
			@RequestParam(name = "villageId", required = false) Integer villageId,
			@RequestParam(name = "surveyNo", required = false) String surveyNo,
			@RequestParam(name = "khataNumbers", required = false) Set<String> khataNumbers) {

		// Step 1: Generate the PDF and save it to the server.
		service.generateAndSaveRor1BMultiplePdfs(districtId, mandalId, villageId, surveyNo, khataNumbers);

		// Step 2: Return the saved file details as JSON.
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("message", "PDF saved successfully",
				"fileName", "savedPath.getFileName().toString()", "path", "savedPath.toString()"));

	}

	private static void requireValidTerm(String name, String value) {
		if (value != null && !value.isBlank() && !SEARCH_TERM.matcher(value.trim()).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " contains invalid characters");
		}
	}
}
