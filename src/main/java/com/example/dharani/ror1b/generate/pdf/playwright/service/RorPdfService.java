package com.example.dharani.ror1b.generate.pdf.playwright.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import com.example.dharani.common.util.CommonUtils;
import com.example.dharani.ror1b.generate.pdf.playwright.model.RorRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Margin;

import jakarta.annotation.PreDestroy;

@Service("playwrightRorPdfService")
public class RorPdfService {

	// Reads the PDF storage location from application.yml.
	// Allows different storage locations for local and production environments.
	@Value("${app.storage.ror1b}")
	private String ror1bStorageLocation;

	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
	private static final int FASLI_OFFSET = 590;
	private static final String TITLE_1 = "తెలంగాణ ప్రభుత్వము, రెవెన్యూశాఖ, భూమి రికార్డుల కంప్యూటీకరణ";
	private static final String TITLE_2 = "1-బి నమూనా(ROR)";
	private static final String AREA_UNITS = "ఏ./గుం."; // hard-coded
	private static final Map<String, String> LAND_NATURE_TE = Map.of("Metta/ Dry", "మెట్ట/ఖుష్కి", "Tari/ Wet",
			"తరి/తర్");

	private final ObjectMapper mapper;
	private final String fontCss;
	private Playwright playwright;
	private Browser browser;

	public RorPdfService(ObjectMapper mapper) {
		this.mapper = mapper;
		this.fontCss = loadFontCss();
	}

	/* ---------------- data ---------------- */

	public List<RorRecord> search(String khata, String surveyNo) throws Exception {
		try (InputStream in = new ClassPathResource("ror-data.json").getInputStream()) {
			List<RorRecord> all = mapper.readValue(in, new TypeReference<List<RorRecord>>() {
			});
			return all.stream()
					.filter(r -> blank(khata) || khata.trim().equals(r.pattadarDetails().get("Khata Number")))
					.filter(r -> blank(surveyNo)
							|| surveyNo.trim().equals(r.landDetails().get("Survey No./ Sub-Division No.")))
					.toList();
		}
	}

	/* ---------------- pdf ---------------- */

	public synchronized byte[] generate(List<RorRecord> records, Integer fasliOverride) {
		LocalDate today = LocalDate.now(IST);
		int fasli = fasliOverride != null ? fasliOverride : currentFasli(today);
		String html = buildHtml(records, fasli, today);

		if (browser == null) {
			playwright = Playwright.create(); // first run downloads Chromium
			browser = playwright.chromium().launch();
		}
		try (Page page = browser.newPage()) {
			page.setContent(html);
			page.evaluate("document.fonts.ready.then(() => true)");
			return page.pdf(new Page.PdfOptions().setFormat("A4").setPrintBackground(true)
					.setMargin(new Margin().setTop("18mm").setBottom("18mm").setLeft("15mm").setRight("15mm")));
		}
	}

	@PreDestroy
	public void close() {
		if (browser != null)
			browser.close();
		if (playwright != null)
			playwright.close();
	}

	/* ---------------- fasli ---------------- */

	/** Fasli year runs 1 July - 30 June. 2026-27 -> 2026 - 590 = 1436. */
	static int currentFasli(LocalDate today) {
		int startYear = today.getMonthValue() >= 7 ? today.getYear() : today.getYear() - 1;
		return startYear - FASLI_OFFSET;
	}

	/** 1436 -> "2026-27" */
	static String yearLabel(int fasli) {
		int start = fasli + FASLI_OFFSET;
		return start + "-" + String.format("%02d", (start + 1) % 100);
	}

	/* ---------------- html ---------------- */

	private String buildHtml(List<RorRecord> records, int fasli, LocalDate today) {
		Map<String, String> first = records.get(0).pattadarDetails(); // same for every row

		StringBuilder rows = new StringBuilder();
		int n = 1;
		for (RorRecord r : records) {
			Map<String, String> p = r.pattadarDetails();
			Map<String, String> l = r.landDetails();
			String nature = l.getOrDefault("Classification of Land", "");
			rows.append("<tr>").append(td(String.valueOf(n++)))
					.append(td(beforeSlash(p.get("Pattadar / Authorised Person Name"))))
					.append(td(beforeSlash(p.get("Father / Husband's Name")))).append(td(p.get("Khata Number")))
					.append(td(l.get("Survey No./ Sub-Division No.")))
					.append(td(LAND_NATURE_TE.getOrDefault(nature, nature))).append(td(l.get("Total Extent (Ac. Gts)")))
					.append("</tr>");
		}

		String date = today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

		return "<html><head><meta charset=\"UTF-8\"><style>" + fontCss
				+ "body{font-family:'RorTelugu','Noto Sans Telugu','Gautami','Nirmala UI',sans-serif;font-size:12px;color:#000}"
				+ "h3{text-align:center;font-weight:normal;font-size:16px;margin:0 0 4px;line-height:1.6}"
				+ ".date{text-align:right;font-weight:bold;margin:18px 0}"
				+ "table{border-collapse:collapse;width:100%;table-layout:fixed}" + ".info{font-size:10px}"
				+ ".info td{padding:2px 3px;vertical-align:top;font-size:10px}.info td.l{font-weight:bold;width:16%;font-size:10px}"
				+ ".ror{width:100%;table-layout:fixed;font-size:10px}"
				+ ".ror th,.ror td{border:1px solid #444;padding:6px 5px;text-align:left;vertical-align:middle;overflow-wrap:break-word;word-wrap:break-word;white-space:normal;box-sizing:border-box;line-height:1.3}"
				+ ".ror th{text-align:center;font-weight:bold}"
				+ ".ror td:nth-child(1),.ror td:nth-child(4),.ror td:nth-child(7){text-align:center}"
				+ ".ror thead{display:table-header-group}.ror tr{page-break-inside:avoid}"
				+ ".ror tr.n td{text-align:center;font-weight:bold}" + "</style></head><body>" + "<h3>" + TITLE_1
				+ "<br>" + TITLE_2 + "</h3>" + "<div class=\"date\">తేదీ : " + date + "</div>"
				+ "<table class=\"info\">" + "<tr><td class=\"l\">జిల్లా</td><td>: "
				+ esc(afterBar(first.get("District"))) + "</td>" + "<td class=\"l\">మండలము</td><td>: "
				+ esc(afterBar(first.get("Mandal"))) + "</td></tr>" + "<tr><td class=\"l\">గ్రామము</td><td>: "
				+ esc(afterBar(first.get("Village"))) + "</td>" + "<td class=\"l\">విస్తీర్ణము యూనిట్లు</td><td>: "
				+ AREA_UNITS + "</td></tr>" + "<tr><td class=\"l\">ఫసిల్</td><td>: " + fasli + "</td>"
				+ "<td class=\"l\">సంవత్సరం</td><td>: " + yearLabel(fasli) + "</td></tr>" + "</table>"
				+ "<table class=\"ror\"><colgroup>" + "<col style=\"width:7%\">" + "<col style=\"width:18%\">"
				+ "<col style=\"width:17%\">" + "<col style=\"width:11%\">" + "<col style=\"width:15%\">"
				+ "<col style=\"width:16%\">" + "<col style=\"width:16%\">" + "</colgroup><thead>"
				+ "<tr><th>వరుస నెం.</th><th>పట్టాదారు పేరు</th><th>(తండ్రి/ భర్త పేరు)</th>"
				+ "<th>ఖాతా నెంబరు</th><th>సర్వే నెంబర్</th><th>భూమి వివరణ</th><th>మొత్తం విస్తీర్ణము</th></tr>"
				+ "<tr class=\"n\"><td>(1)</td><td>(2)</td><td>(3)</td><td>(4)</td><td>(5)</td><td>(6)</td><td>(7)</td></tr>"
				+ "</thead><tbody>" + rows + "</tbody></table>" + "</body></html>";
	}

	private static String loadFontCss() {
		ClassPathResource f = new ClassPathResource("fonts/NotoSansTelugu-Regular.ttf");
		if (!f.exists())
			return ""; // falls back to a system Telugu font
		try (InputStream in = f.getInputStream()) {
			String b64 = Base64.getEncoder().encodeToString(in.readAllBytes());
			return "@font-face{font-family:'RorTelugu';src:url(data:font/ttf;base64," + b64 + ") format('truetype');}";
		} catch (Exception e) {
			return "";
		}
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}

	private static String td(String v) {
		return "<td>" + esc(v) + "</td>";
	}

	private static String esc(String v) {
		return v == null ? "" : HtmlUtils.htmlEscape(v);
	}

	/** "Siddipet | సిద్దిపేట్" -> "సిద్దిపేట్" */
	private static String afterBar(String v) {
		if (v == null)
			return "";
		int i = v.lastIndexOf('|');
		return (i >= 0 ? v.substring(i + 1) : v).trim();
	}

	/** "వంగ బాల్ రెడ్డి / VANGA BAL REDDY" -> "వంగ బాల్ రెడ్డి" */
	private static String beforeSlash(String v) {
		if (v == null)
			return "";
		int i = v.indexOf(" / ");
		return (i >= 0 ? v.substring(0, i) : v).trim();
	}

	/**
	 * Generates the ROR PDF and saves it in:
	 *
	 * src/main/resources/ror1b/generated-pdfs/{district}/{mandal}/
	 *
	 * The folder is created automatically if it does not exist.
	 */
	public Path savePdf(List<RorRecord> records, Integer fasliOverride) throws IOException {

		// Check whether records are available.
		if (records == null || records.isEmpty()) {
			throw new IllegalArgumentException("No records available to generate PDF");
		}

		// Generate the PDF using your existing method.
		byte[] pdfBytes = generate(records, fasliOverride);

		// Read the location details from the first JSON record.
		Map<String, String> details = records.get(0).pattadarDetails();

		// Extract English names from values like:
		// "Siddipet | సిద్దిపేట్" -> "Siddipet"
		String village = getEnglishName(details.get("Village"));
		String mandal = getEnglishName(details.get("Mandal"));
		String district = getEnglishName(details.get("District"));

		// Generate a date-specific filename using Indian local Date & time.
		// Example: Mittapalle_Siddipet(Urban)_Siddipet_ROR1B_dd-MM-yyyy_05-13 pm.pdf
		String filename = sanitize(village) + "_" + sanitize(mandal) + "_" + sanitize(district) + "_ROR1B_"
				+ CommonUtils.getCurrentDateAsDDMMYYYYHHMMAMPM() + ".pdf";

		// Define the base output directory inside src/main/resources.
		Path baseDirectory = Paths.get(ror1bStorageLocation).toAbsolutePath().normalize();

		// Create the district folder.
		Path districtDirectory = baseDirectory.resolve(sanitize(district));

		// Create the mandal folder inside the district folder.
		Path mandalDirectory = districtDirectory.resolve(sanitize(mandal));

		// Create all missing parent folders automatically.
		Files.createDirectories(mandalDirectory);

		// Build the final file path.
		Path outputFile = mandalDirectory.resolve(filename);

		// Save the generated PDF bytes to disk.
		Files.write(outputFile, pdfBytes);

		// Return the absolute path of the saved PDF.
		return outputFile.toAbsolutePath();
	}

	/**
	 * Returns the English part of a JSON value separated by "|".
	 */
	private String getEnglishName(String value) {

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
	 * Removes characters that are unsafe in folder and file names. Keeps letters,
	 * numbers, parentheses, underscores, hyphens and dots.
	 */
	private String sanitize(String value) {

		if (value == null || value.isBlank()) {
			return "Unknown";
		}

		// Remove Windows-invalid filename characters and whitespace.
		return value.trim().replaceAll("[\\\\/:*?\"<>|\\s]+", "");
	}
}
