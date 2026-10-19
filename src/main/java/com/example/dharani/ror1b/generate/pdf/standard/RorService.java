package com.example.dharani.ror1b.generate.pdf.standard;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.dharani.common.util.CommonUtils;
import com.example.dharani.ror1b.generate.pdf.standard.pdfrender.Ror1BHeader;
import com.example.dharani.ror1b.generate.pdf.standard.pdfrender.Ror1BPdfRenderer;

@Service
public class RorService {

	// Reads the PDF storage location from application.yml.
	// Allows different storage locations for local and production environments.
	@Value("${app.storage.ror1b}")
	private String ror1bStorageLocation;

	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final RorDataRepository repository;
	private final Ror1BPdfRenderer renderer;
	private final Clock clock;

	@Autowired
	public RorService(RorDataRepository repository, Ror1BPdfRenderer renderer) {
		this(repository, renderer, Clock.system(IST));
	}

	RorService(RorDataRepository repository, Ror1BPdfRenderer renderer, Clock clock) {
		this.repository = repository;
		this.renderer = renderer;
		this.clock = clock;
	}

	public List<Ror1bRecord> search(String khataNumber, String surveyNo) {
		return repository.find(khataNumber, surveyNo);
	}

	/**
	 * @param fasliOverride null = calculate from today's date (start year - 590,
	 *                      year starts 1 July)
	 */
	public void writePdf(List<Ror1bRecord> records, Integer fasliOverride, OutputStream out) throws IOException {
		LocalDate today = LocalDate.now(clock);
		int fasli = fasliOverride != null ? fasliOverride : FasliCalendar.currentFasli(today);
		renderer.render(header(records.get(0), fasli, today), records, out);
	}

	/**
	 * District / mandal / village are the same on every row, so the first row is
	 * used.
	 */
	static Ror1BHeader header(Ror1bRecord first, int fasli, LocalDate today) {
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
	 * Saves the generated ROR 1-B PDF to the local project directory.
	 *
	 * Example output: src/main/resources/ror1b/generated-pdfs/
	 * Siddipet/SiddipetUrban/Mittapalle_SiddipetUrban_Siddipet_ROR1B.pdf
	 *
	 * The existing renderer is reused, so Telugu text and formatting remain the
	 * same as the normal PDF API.
	 */
	public Path savePdf(List<Ror1bRecord> records, Integer fasliOverride) throws IOException {

		// Step 1: Check whether matching records exist.
		if (records == null || records.isEmpty()) {
			throw new IllegalArgumentException("No records available to generate PDF");
		}

		// Step 2: Calculate today's date and the Fasli year.
		// If fasliOverride is provided, use that year instead.
		LocalDate today = LocalDate.now(clock);

		int fasli = fasliOverride != null ? fasliOverride : FasliCalendar.currentFasli(today);

		// Step 3: Create the same PDF header used by your existing API.
		Ror1BHeader pdfHeader = header(records.get(0), fasli, today);

		// Step 4: Read district, mandal, and village from the first record.
		// The JSON may contain values such as "Siddipet | సిద్దిపేట్".
		Map<String, String> details = records.get(0).pattadarDetails();

		// Step 5: Generate the output path for the ROR 1B PDF.
		Path outputFile = CommonUtils.generate_RoR_1B_PdfPath(details, ror1bStorageLocation);

		// Step 6: Write the PDF directly to the file.
		// This avoids buffering the entire PDF in a byte array.
		try (var outputStream = Files.newOutputStream(outputFile)) {
			renderer.render(pdfHeader, records, outputStream);
		}

		// Step 7: Return the absolute path of the saved PDF.
		return outputFile.toAbsolutePath();
	}

}
