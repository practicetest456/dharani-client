package com.example.dharani.ror1b.generate.pdf.optimized;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.dharani.common.model.Ror1BLandDetails;
import com.example.dharani.common.util.CommonUtils;
import com.example.dharani.masterdata.claud.mandal.MasterData;
import com.example.dharani.masterdata.claud.mandal.MasterDataService;
import com.example.dharani.publicdata.service.PublicDataService;
import com.example.dharani.ror1b.generate.pdf.optimized.pdfrenderer.Ror1BHeader;
import com.example.dharani.ror1b.generate.pdf.optimized.pdfrenderer.Ror1BPdfRenderer;
import com.example.dharani.service.KhataResult;
import com.example.dharani.service.KhataService;

@Service("lessSizePdfRorService")
public class RorService {

	private static final Logger log = LoggerFactory.getLogger(RorService.class);
	
	// Reads the PDF storage location from application.yml.
	// Allows different storage locations for local and production environments.
	@Value("${app.storage.ror1b}")
	private String ror1bStorageLocation;

	private final MasterDataService masterDataservice;
	private final KhataService khataService;
	private final PublicDataService publicDataService;

	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final RorDataRepository repository;
	private final Ror1BPdfRenderer renderer;
	private final Clock clock;

	@Autowired
	public RorService(MasterDataService masterDataservice, KhataService khataService,
			PublicDataService publicDataService, RorDataRepository repository, Ror1BPdfRenderer renderer) {
		this(masterDataservice, khataService, publicDataService, repository, renderer, Clock.system(IST));
	}

	RorService(MasterDataService masterDataservice, KhataService khataService, PublicDataService publicDataService,
			RorDataRepository repository, Ror1BPdfRenderer renderer, Clock clock) {
		this.masterDataservice = masterDataservice;
		this.khataService = khataService;
		this.publicDataService = publicDataService;
		this.repository = repository;
		this.renderer = renderer;
		this.clock = clock;

	}

	public List<Ror1BLandDetails> search(String khataNumber, String surveyNo) {
		return repository.find(khataNumber, surveyNo);
	}

	/**
	 * @param fasliOverride null = calculate from today's date (start year - 590,
	 *                      year starts 1 July)
	 */
	public void writePdf(List<Ror1BLandDetails> records, Integer fasliOverride, OutputStream out) throws IOException {
		LocalDate today = LocalDate.now(clock);
		int fasli = fasliOverride != null ? fasliOverride : FasliCalendar.currentFasli(today);
		renderer.render(header(records.get(0), fasli, today), records, out);
	}

	/**
	 * District / mandal / village are the same on every row, so the first row is
	 * used.
	 */
	static Ror1BHeader header(Ror1BLandDetails first, int fasli, LocalDate today) {
		return new Ror1BHeader(afterBar(first.pattadarDetails().get("District")),
				afterBar(first.pattadarDetails().get("Mandal")), afterBar(first.pattadarDetails().get("Village")),
				fasli, FasliCalendar.yearLabel(fasli), today.format(DATE));
	}

	/** "Siddipet | సిద్దిపేట్" -> "సిద్దిపేట్" */
	static String afterBar(String v) {
		if (v == null) {
			return "";
		}
		int i = v.lastIndexOf('|');
		return (i >= 0 ? v.substring(i + 1) : v).trim();
	}

	/**
	 * Generates the ROR 1-B PDF and saves it to a dynamically created directory.
	 *
	 * Folder structure: ror1bs/generated-pdfs/{district}/{mandal}/{filename}.pdf
	 *
	 * The existing writePdf() method is reused to render the PDF directly into the
	 * output file without storing the entire PDF in memory.
	 *
	 * @param records       ROR records to include in the PDF
	 * @param fasliOverride Optional Fasli year; null uses the calculated year
	 * @return Absolute path of the generated PDF
	 * @throws IOException If directory creation or PDF writing fails
	 */
	public Path generateRor1BPdf(List<Ror1BLandDetails> records, Integer fasliOverride) throws IOException {

		// Validate the input before accessing the first record.
		if (records == null || records.isEmpty()) {
			throw new IllegalArgumentException("ROR records must not be empty");
		}

		// Use the first record to obtain the common location details.
		Ror1BLandDetails first = records.get(0);
		Map<String, String> details = first.pattadarDetails();

		// Extract English location names for readable PDF filenames.
		String village = getEnglishName(details.get("Village"));
		String mandal = getEnglishName(details.get("Mandal"));
		String district = getEnglishName(details.get("District"));

		// Sanitize location names before using them in filesystem paths.
		String safeVillage = sanitizeFilename(village);
		String safeMandal = sanitizeFilename(mandal);
		String safeDistrict = sanitizeFilename(district);

		// Generate a date-specific filename using Indian local Date & time.
		String filename = safeVillage + "_" + safeMandal + "_" + safeDistrict + "_ROR1B_"
				+ CommonUtils.getCurrentDateAsDDMMYYYYHHMMAMPM() + ".pdf";

		// Define the root directory for generated PDFs.
		// This location works independently of the packaged JAR resources.
		Path baseDirectory = Paths.get(ror1bStorageLocation).toAbsolutePath().normalize();

		// Create the district and mandal folder hierarchy.
		Path outputDirectory = baseDirectory.resolve(safeDistrict).resolve(safeMandal).normalize();

		// Prevent the generated directory from escaping the base directory.
		if (!outputDirectory.startsWith(baseDirectory)) {
			throw new IOException("Invalid PDF output directory");
		}

		// Create all missing directories automatically.
		Files.createDirectories(outputDirectory);

		// Resolve the final PDF path and validate it.
		Path outputFile = outputDirectory.resolve(filename).normalize();

		if (!outputFile.startsWith(outputDirectory)) {
			throw new IOException("Invalid PDF output filename");
		}

		// Write the generated PDF directly to disk.
		// An existing file with the same name is overwritten.
		try (OutputStream out = Files.newOutputStream(outputFile, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {

			// Reuse the existing renderer and Fasli calculation logic.
			writePdf(records, fasliOverride, out);
		}

		// Return the saved PDF path to the controller.
		return outputFile;
	}

	public void generateAndSaveRor1BMultiplePdfs(Integer districtId, Integer mandalId, Integer villageId, String surveyNo,
			Set<String> khataNumbers) {

		if (districtId != null && mandalId != null && villageId != null && khataNumbers != null
				&& !khataNumbers.isEmpty()) {

			generateAndSaveRor1bDataJsonForkhataNumberOfVillage(districtId, mandalId, villageId, khataNumbers);

		} else if (districtId != null && mandalId != null && villageId != null) {

			generateRor1bDataJsonForVillageByVillageId(districtId, mandalId, villageId);

		} else if (districtId != null && mandalId != null) {

			generateAndSaveRor1bDataJsonForAllVillagesByManadal(districtId, mandalId);

		}

	}
	
	public List<Ror1BLandDetails> generateRor1BJson(Integer districtId, Integer mandalId, Integer villageId, String surveyNo,
			Set<String> khataNumbers) {

		List<Ror1BLandDetails> ror1BLandDetailsList = null;

		if (districtId != null && mandalId != null && villageId != null && khataNumbers != null
				&& !khataNumbers.isEmpty()) {

			ror1BLandDetailsList = ror1bLandDetailsListForkhataNumberOfVillage(districtId, mandalId, villageId,
					khataNumbers);
		} else if (districtId != null && mandalId != null && villageId != null) {

			List<KhataResult> khataResultList = khataService.fetchSurveyKhataNos(
					masterDataservice.fetchVillageSurveyNosAsJson(villageId), String.valueOf(villageId));

			ror1BLandDetailsList = publicDataService.fetchParsedForAllSurveys(khataResultList,
					String.valueOf(villageId), String.valueOf(mandalId), String.valueOf(districtId));

		}
		return ror1BLandDetailsList;
	}

	/**
	 * Generates ROR-1B PDFs for all villages under the specified mandal. Fetches
	 * the village list, retrieves survey-wise khata and land details for each
	 * village and generates a PDF when land details are available.
	 *
	 * @param districtId the ID of the district
	 * @param mandalId   the ID of the mandal
	 */
	private void generateAndSaveRor1bDataJsonForAllVillagesByManadal(Integer districtId, Integer mandalId) {
		MasterData villagesList = masterDataservice.fetchVillagesAsJson(mandalId);
		if (villagesList == null || villagesList.items() == null) {
			return;
		}

		for (MasterData.Item item : villagesList.items()) {
			int villageId = (int) item.id();

			List<KhataResult> khataResultList = khataService.fetchSurveyKhataNos(
					masterDataservice.fetchVillageSurveyNosAsJson(villageId), String.valueOf(villageId));

			List<Ror1BLandDetails> ror1BLandDetailsList = publicDataService.fetchParsedForAllSurveys(khataResultList,
					String.valueOf(villageId), String.valueOf(mandalId), String.valueOf(districtId));

			try {
				if (ror1BLandDetailsList != null && !ror1BLandDetailsList.isEmpty()) {
					Ror1BLandDetails firstRecord = ror1BLandDetailsList.get(0);
					Map<String, String> details = firstRecord.pattadarDetails();

					String village = CommonUtils.getEnglishName(details.get("Village"));
					String mandal = CommonUtils.getEnglishName(details.get("Mandal"));
					log.info("Pattadar and Land details fetching is Completed for village: {}", village + "_" + mandal);

					generateRor1BPdf(ror1BLandDetailsList, null);
					log.info("Generate Ror1B Pdf is Completed for village: {}", village + "_" + mandal);

				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	
	
	/**
	 * Generates the ROR-1B PDF for all survey numbers associated with the specified village.
	 * Fetches survey-wise khata details, retrieves the corresponding land details,
	 * and generates the PDF using the collected data.
	 *
	 * @param districtId the ID of the district
	 * @param mandalId   the ID of the mandal
	 * @param villageId  the ID of the village
	 */
	private void generateRor1bDataJsonForVillageByVillageId(Integer districtId, Integer mandalId, Integer villageId) {

		List<KhataResult> khataResultList = khataService.fetchSurveyKhataNos(
				masterDataservice.fetchVillageSurveyNosAsJson(villageId), String.valueOf(villageId));

		List<Ror1BLandDetails> ror1BLandDetailsList = publicDataService.fetchParsedForAllSurveys(khataResultList,
				String.valueOf(villageId), String.valueOf(mandalId), String.valueOf(districtId));

		try {
			generateRor1BPdf(ror1BLandDetailsList, null);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Generates the ROR-1B PDF for the specified khata numbers within a village.
	 * Fetches survey-wise khata details, filters records matching the given khata
	 * numbers, and retrieves the corresponding land details for PDF generation.
	 *
	 * @param districtId   the ID of the district
	 * @param mandalId     the ID of the mandal
	 * @param villageId    the ID of the village
	 * @param khataNumbers the set of khata numbers to filter
	 */
	public void generateAndSaveRor1bDataJsonForkhataNumberOfVillage(Integer districtId, Integer mandalId, Integer villageId,
			Set<String> khataNumbers) {

		List<Ror1BLandDetails> ror1BLandDetailsList = ror1bLandDetailsListForkhataNumberOfVillage(districtId, mandalId,
				villageId, khataNumbers);

		try {
			generateRor1BPdf(ror1BLandDetailsList, null);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private List<Ror1BLandDetails> ror1bLandDetailsListForkhataNumberOfVillage(Integer districtId, Integer mandalId,
			Integer villageId, Set<String> khataNumbers) {
		List<KhataResult> khataResultList = khataService.fetchSurveyKhataNos(
				masterDataservice.fetchVillageSurveyNosAsJson(villageId), String.valueOf(villageId));

		List<KhataResult> filteredList = new ArrayList<>();

		for (KhataResult result : khataResultList) {
			List<String> khataNos = result.khataNos();

			if (khataNos == null || khataNos.isEmpty()) {
				continue;
			}

			for (String khataNo : khataNos) {
				if (khataNumbers.contains(khataNo)) {
					filteredList.add(result);
					break; // Avoid adding the same record more than once
				}
			}
		}
		filteredList.sort(Comparator.comparing((KhataResult r) -> r.khataNos().get(0)));
		List<Ror1BLandDetails> ror1BLandDetailsList = publicDataService.fetchParsedForAllSurveys(filteredList,
				String.valueOf(villageId), String.valueOf(mandalId), String.valueOf(districtId));
		
		return ror1BLandDetailsList;
	}

	/**
	 * Extracts the English name from a value such as "Siddipet | సిద్దిపేట్".
	 *
	 * If the value is missing, returns a safe default.
	 */
	private static String getEnglishName(String value) {

		if (value == null || value.isBlank()) {
			return "Unknown";
		}

		int separator = value.indexOf('|');

		String englishName = separator >= 0 ? value.substring(0, separator).trim() : value.trim();

		return englishName.isBlank() ? "Unknown" : englishName;
	}

	/**
	 * Removes spaces and characters that are not suitable for filenames.
	 *
	 * Input : "Siddipet (Urban) / Telangana"
	 * 
	 * Output: "Siddipet(Urban)Telangana"
	 */
	public static String sanitizeFilename(String value) {

		// Use a fallback when the value is missing.
		if (value == null || value.isBlank()) {
			return "Unknown";
		}

		// Remove spaces and characters that are not suitable for filenames.
		return value.trim().replaceAll("[\\\\/:*?\"<>|\\s]+", "");
	}

}
