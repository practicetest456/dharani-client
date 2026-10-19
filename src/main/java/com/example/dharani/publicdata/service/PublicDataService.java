package com.example.dharani.publicdata.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.dharani.common.model.Ror1BLandDetails;
import com.example.dharani.publicdata.client.PublicDataClient;
import com.example.dharani.publicdata.config.PublicDataProperties;
import com.example.dharani.publicdata.encryption.BhubharatiSurveyNosEncryptor;
import com.example.dharani.publicdata.model.PublicDataRequest;
import com.example.dharani.publicdata.model.PublicDataResponse;
import com.example.dharani.service.KhataResult;
import com.example.dharani.service.KhataService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PublicDataService {

	private static final Logger log = LoggerFactory.getLogger(PublicDataService.class);
	  
	private static final int MAX_CONCURRENCY = 10;   // matches dharani.concurrency in your yml
	private static final int MAX_ATTEMPTS = 3;

	private final ExecutorService fetchExecutor = Executors.newFixedThreadPool(
			MAX_CONCURRENCY,
			r -> {
				Thread t = new Thread(r, "ror1b-fetch");
				t.setDaemon(true);
				return t;
			});
	
	private final PublicDataClient client;
	private final LandDetailsParser parser;
	private final LandDetailsHtmlRenderer renderer;
	private final PublicDataProperties defaults;
	private final ObjectMapper objectMapper;
	private final KhataService khataService;

	public PublicDataService(PublicDataClient client, LandDetailsParser parser, LandDetailsHtmlRenderer renderer,
			PublicDataProperties defaults, ObjectMapper objectMapper, KhataService khataService) {
		this.client = client;
		this.parser = parser;
		this.renderer = renderer;
		this.defaults = defaults;
		this.objectMapper = objectMapper;
		this.khataService = khataService;
	}

	/**
	 * Raw response. Any field left empty in the request is taken from
	 * application.yml.
	 */
	public PublicDataResponse fetch(PublicDataRequest r) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("villageId", nz(r.villageId(), defaults.villageId()));
		params.put("flagToSearch", nz(r.flagToSearch(), defaults.flagToSearch()));
		params.put("searchData", nz(r.searchData(), defaults.searchData()));
		params.put("flagval", nz(r.flagval(), defaults.flagval()));
		params.put("district", nz(r.district(), defaults.district()));
		params.put("mandal", nz(r.mandal(), defaults.mandal()));
		params.put("divi", nz(r.divi(), defaults.divi()));
		params.put("khataNoIdselect", nz(r.khataNoIdselect(), defaults.khataNoIdselect()));
		params.put("ReqType", nz(r.reqType(), defaults.reqType()));
		params.put("captcha", nz(r.captcha(), defaults.captcha()));
		return client.post(params);
	}

	public Ror1BLandDetails fetchParsed(PublicDataRequest r) {
		return parser.parse(fetch(r).body());
	}

	public String fetchHtmlView(PublicDataRequest r) {
		return renderer.render(fetchParsed(r));
	}

	private static String nz(String value, String fallback) {
		if (value != null && !value.isBlank()) {
			return value;
		}
		return fallback == null ? "" : fallback;
	}

	public List<Ror1BLandDetails> fetchParsedForAllSurveys() {

		List<KhataResult> rows = khataService.fetchAllFromFile();
		List<Ror1BLandDetails> results = fetchParsedForAllSurveys(rows, null, null, null);
		return results;
	}
	
	public List<Ror1BLandDetails> fetchParsedForAllSurveys(List<KhataResult> rows, String villageId, String mandalId,
			String districtId) {

		if (rows == null || rows.isEmpty()) {
			return new ArrayList<>();
		}

		final String village  = isBlank(villageId)  ? defaults.villageId() : villageId;
		final String district = isBlank(districtId) ? defaults.district()  : districtId;
		final String mandal   = isBlank(mandalId)   ? defaults.mandal()    : mandalId;
		final String flagToSearch = defaults.flagToSearch();
		final String flagval      = defaults.flagval();
		final String divi         = defaults.divi();
		final String reqType      = defaults.reqType();
		final String captcha      = defaults.captcha();

		final AtomicInteger failed = new AtomicInteger();
		List<CompletableFuture<Ror1BLandDetails>> futures = new ArrayList<>();

		for (KhataResult row : rows) {

			if (row.error() != null && !row.error().isBlank()) {
				continue;
			}

			final String surveyNo = row.surveyNo();
			final List<String> khataNos = row.khataNos();

			if (surveyNo == null || surveyNo.isBlank() || khataNos == null || khataNos.isEmpty()) {
				continue;
			}

			final String encryptedSearchData;
			try {
				encryptedSearchData = BhubharatiSurveyNosEncryptor.encrypt(surveyNo);
			} catch (Exception e) {
				log.error("Failed to encrypt survey number: {}", surveyNo, e);
				continue;
			}

			for (String khataNo : khataNos) {
				final PublicDataRequest request = new PublicDataRequest(
						village, flagToSearch, encryptedSearchData, flagval,
						district, mandal, divi, khataNo, reqType, captcha);

				futures.add(CompletableFuture.supplyAsync(
						() -> fetchWithRetry(request, surveyNo, khataNo, failed),
						fetchExecutor));
			}
		}

		List<Ror1BLandDetails> results = new ArrayList<>(futures.size());
		for (CompletableFuture<Ror1BLandDetails> f : futures) {
			Ror1BLandDetails r = f.join();
			if (r != null) {
				results.add(r);
			}
		}

		if (failed.get() > 0) {
			log.warn("fetchParsedForAllSurveys: {} of {} requests failed after {} attempts",
					failed.get(), futures.size(), MAX_ATTEMPTS);
		}
		return results;
	}
	
	private Ror1BLandDetails fetchWithRetry(PublicDataRequest request, String surveyNo,
			String khataNo, AtomicInteger failed) {
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				PublicDataResponse resp = fetch(request);
				if (resp.status() == 429 || resp.status() >= 500) {
					throw new IllegalStateException("Portal returned status " + resp.status());
				}
				return parser.parse(resp.body());
			} catch (Exception e) {
				if (attempt == MAX_ATTEMPTS) {
					failed.incrementAndGet();
					log.warn("Failed survey {} khata {}: {}", surveyNo, khataNo, e.toString());
					return null;
				}
				try {
					Thread.sleep(500L * attempt);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					failed.incrementAndGet();
					return null;
				}
			}
		}
		return null;
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}
}