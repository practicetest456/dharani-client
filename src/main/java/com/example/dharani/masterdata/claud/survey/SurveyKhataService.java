package com.example.dharani.masterdata.claud.survey;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.dharani.masterdata.claud.portal.BhubharatiPortalClient;
import com.example.dharani.masterdata.claud.portal.HtmlOptions;
import com.example.dharani.masterdata.claud.portal.PortalException;

/**
 * Survey numbers of a village and the khata numbers of each.
 *
 * The portal answers one survey number at a time, and a village can have thousands. So: the khata look-ups run in
 * parallel on virtual threads (at most bhubharati.parallelism calls to the portal at the same moment, default 8,
 * to stay polite), every answer is cached per village (bhubharati.survey-khatas.cache-ttl, default 12h), a failed
 * call is retried, and a survey number that still fails is reported in its own {@code error} field only. A second
 * request for the same village is therefore answered from memory, and one that was only partly successful fetches
 * just the missing survey numbers.
 */
@Service("claudbhubharatiSurveyKhataService")
public class SurveyKhataService implements DisposableBean {

	private static final int ATTEMPTS_PER_SURVEY = 3;
	private static final int MAX_CACHED_VILLAGES = 100;

	/** Everything known about one village. */
	private static final class Village {
		final long loadedAt;
		final List<String> surveyNos;
		final Map<String, List<String>> khatas = new ConcurrentHashMap<>();

		Village(long loadedAt, List<String> surveyNos) {
			this.loadedAt = loadedAt;
			this.surveyNos = surveyNos;
		}
	}

	private final BhubharatiPortalClient client;
	private final Semaphore permits;
	private final Duration cacheTtl;
	private final Clock clock;
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
	private final Map<String, Village> villages = Collections.synchronizedMap(
			new LinkedHashMap<>(16, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, Village> eldest) {
					return size() > MAX_CACHED_VILLAGES;
				}
			});

	@Autowired
	public SurveyKhataService(BhubharatiPortalClient client, @Value("${bhubharati.parallelism:8}") int parallelism,
			@Value("${bhubharati.survey-khatas.cache-ttl:12h}") Duration cacheTtl) {
		this(client, parallelism, cacheTtl, Clock.systemUTC());
	}

	SurveyKhataService(BhubharatiPortalClient client, int parallelism, Duration cacheTtl, Clock clock) {
		this.client = client;
		this.permits = new Semaphore(Math.max(1, parallelism), true); // fair: calls start in the order they were queued
		this.cacheTtl = cacheTtl;
		this.clock = clock;
	}

	/** All survey numbers of the village, in the portal's order. */
	public List<String> surveyNos(String villId, boolean refresh) {
		return village(villId, refresh).surveyNos;
	}

	/** Khata numbers of one survey number (an empty list when the survey has none). */
	public List<String> khataNos(String villId, String surveyNo) {
		Village village = village(villId, false);
		List<String> cached = village.khatas.get(surveyNo);
		if (cached != null) {
			return cached;
		}
		List<String> khatas = fetchKhatas(villId, surveyNo);
		village.khatas.put(surveyNo, khatas);
		return khatas;
	}

	/**
	 * Starts the work for the whole village and returns one future per survey number, in the portal's order.
	 * Reading the futures in order therefore delivers results while the rest is still being fetched.
	 * Throws {@link PortalException} if the survey list itself cannot be read.
	 *
	 * @param includePlaceholder add the portal's "Please select" entry (survey number "0", no khata numbers) first
	 */
	public List<Future<SurveyKhata>> plan(String villId, boolean refresh, boolean includePlaceholder) {
		Village village = village(villId, refresh);
		List<Future<SurveyKhata>> plan = new ArrayList<>(village.surveyNos.size() + 1);
		if (includePlaceholder) {
			plan.add(CompletableFuture.completedFuture(new SurveyKhata("0", List.of(), null)));
		}
		for (String surveyNo : village.surveyNos) {
			plan.add(executor.submit(() -> load(village, villId, surveyNo)));
		}
		return plan;
	}

	/** Stops the work still queued or running for a plan (for example when the client went away). */
	public void cancel(List<Future<SurveyKhata>> plan) {
		for (Future<SurveyKhata> future : plan) {
			future.cancel(true);
		}
	}

	private SurveyKhata load(Village village, String villId, String surveyNo) {
		List<String> cached = village.khatas.get(surveyNo);
		if (cached != null) {
			return new SurveyKhata(surveyNo, cached, null);
		}
		for (int attempt = 1; attempt <= ATTEMPTS_PER_SURVEY; attempt++) {
			try {
				List<String> khatas = fetchKhatas(villId, surveyNo);
				village.khatas.put(surveyNo, khatas);
				return new SurveyKhata(surveyNo, khatas, null);
			} catch (PortalException e) {
				if (Thread.currentThread().isInterrupted()) {
					break;
				}
				if (attempt < ATTEMPTS_PER_SURVEY) {
					try {
						Thread.sleep(250L * attempt);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						break;
					}
				}
			}
		}
		return new SurveyKhata(surveyNo, List.of(),
				"Could not read the khata numbers from the portal (tried " + ATTEMPTS_PER_SURVEY + " times)");
	}

	/** One portal call, limited by the parallelism permits. */
	private List<String> fetchKhatas(String villId, String surveyNo) {
		try {
			permits.acquire();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new PortalException("Cancelled", e);
		}
		try {
			return HtmlOptions.parse(client.khataOptionsHtml(villId, surveyNo)).stream()
					.map(HtmlOptions.Option::value).toList();
		} finally {
			permits.release();
		}
	}

	private Village village(String villId, boolean refresh) {
		Village village = villages.get(villId);
		if (refresh || village == null || isExpired(village)) {
			village = loadVillage(villId, village);
		}
		return village;
	}

	private boolean isExpired(Village village) {
		return clock.millis() - village.loadedAt > cacheTtl.toMillis();
	}

	/** One survey-list read at a time; a newer entry stored by a waiting request is reused (see MandalMasterService). */
	private synchronized Village loadVillage(String villId, Village seen) {
		Village current = villages.get(villId);
		if (current != null && current != seen && !isExpired(current)) {
			return current;
		}
		List<String> surveyNos = HtmlOptions.parse(client.surveyOptionsHtml(villId)).stream()
				.map(HtmlOptions.Option::value).distinct().toList();
		if (surveyNos.isEmpty()) {
			throw new PortalException("The portal returned no survey numbers for village " + villId);
		}
		Village fresh = new Village(clock.millis(), surveyNos);
		villages.put(villId, fresh);
		return fresh;
	}

	@Override
	public void destroy() {
		executor.shutdownNow();
	}
}
