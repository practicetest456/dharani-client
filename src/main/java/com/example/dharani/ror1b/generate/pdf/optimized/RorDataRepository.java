package com.example.dharani.ror1b.generate.pdf.optimized;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;

import com.example.dharani.common.model.Ror1BLandDetails;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reads the ROR JSON (default: classpath:ror-data.json, override with
 * ror.data-location=file:/path/data.json). The parsed list is cached in memory
 * and re-read automatically when the file is edited, so there is no restart
 * needed while you change the JSON.
 */
@Repository("lessSizePdfRorDataRepository")
public class RorDataRepository {

	private static final Logger log = LoggerFactory.getLogger(RorDataRepository.class);

	private record Snapshot(long lastModified, List<Ror1BLandDetails> records) {
	}

	private final Resource resource;
	private final ObjectMapper mapper;
	private volatile Snapshot snapshot;

	public RorDataRepository(@Value("${ror.data-location:classpath:ror-data.json}") String location,
			ResourceLoader loader, ObjectMapper mapper) {
		this.resource = loader.getResource(location);
		this.mapper = mapper;
		if (!resource.exists()) {
			throw new IllegalStateException("ROR data file not found: " + location);
		}
		reload(modified());
	}

	/** Records matching the (optional) khata number and survey number. */
	public List<Ror1BLandDetails> find(String khataNumber, String surveyNo) {
		long modified = modified();
		Snapshot current = snapshot;
		if (modified != current.lastModified()) {
			current = reload(modified);
		}
		return current.records().stream().filter(r -> matches(khataNumber, r.khataNumber()))
				.filter(r -> matches(surveyNo, r.surveyNo())).toList();
	}

	private synchronized Snapshot reload(long modified) {
		Snapshot current = snapshot;
		if (current != null && current.lastModified() == modified) {
			return current;
		}
		try (InputStream in = resource.getInputStream()) {
			List<Ror1BLandDetails> records = List
					.copyOf(mapper.readValue(in, new TypeReference<List<Ror1BLandDetails>>() {
					}));
			snapshot = new Snapshot(modified, records);
			log.info("Loaded {} ROR record(s) from {}", records.size(), resource.getDescription());
			return snapshot;
		} catch (IOException e) {
			if (snapshot != null) { // keep serving the last good data if the edit is half-written
				log.warn("Could not re-read ROR data, keeping previous data: {}", e.toString());
				return snapshot;
			}
			throw new UncheckedIOException("Cannot read ROR data " + resource.getDescription(), e);
		}
	}

	private long modified() {
		try {
			return resource.lastModified();
		} catch (IOException e) {
			return 0L;
		}
	}

	private static boolean matches(String wanted, String actual) {
		if (wanted == null || wanted.isBlank()) {
			return true;
		}
		return actual != null && nfc(wanted.trim()).equals(nfc(actual.trim()));
	}

	/** Telugu can be encoded in more than one way; compare in canonical form. */
	private static String nfc(String s) {
		return Normalizer.normalize(s, Normalizer.Form.NFC);
	}
}
