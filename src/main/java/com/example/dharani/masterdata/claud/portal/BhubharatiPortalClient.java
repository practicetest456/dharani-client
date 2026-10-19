package com.example.dharani.masterdata.claud.portal;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The one place that talks to the Bhubharati citizen portal, exactly the way its own web page does.
 *
 * It first opens the "Know Land Status" page to get a fresh session cookie (nothing is copied from a browser, and an
 * expired session fixes itself), then asks for the dropdown fragments. The client is thread-safe and keeps its
 * connections alive, so many requests can run in parallel over one shared session.
 *
 * Uses only the JDK HTTP client. Optional settings: bhubharati.base-url, bhubharati.timeout (default 15s) and
 * bhubharati.cookie (a fixed Cookie header, only if the portal ever refuses the automatic session).
 */
@Component("claudbhubharatiPortalClient")
public class BhubharatiPortalClient {

	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
			+ "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";
	private static final long SESSION_MAX_AGE_MILLIS = 15 * 60 * 1000L;
	private static final long DEFAULT_MIN_REFRESH_MILLIS = 5_000L;

	private final String baseUrl;
	private final Duration timeout;
	private final String fixedCookie;
	private final long minRefreshMillis;
	private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
	private final HttpClient http;
	private final Object sessionLock = new Object();
	private int sessionGeneration; // guarded by sessionLock
	private long sessionStartedAt; // guarded by sessionLock

	@Autowired
	public BhubharatiPortalClient(@Value("${bhubharati.base-url:https://bhubharati.telangana.gov.in}") String baseUrl,
			@Value("${bhubharati.timeout:15s}") Duration timeout, @Value("${bhubharati.cookie:}") String fixedCookie) {
		this(baseUrl, timeout, fixedCookie, DEFAULT_MIN_REFRESH_MILLIS);
	}

	/**
	 * @param minRefreshMillis shortest time between two forced session renewals
	 *                         (protects the portal in a storm)
	 */
	public BhubharatiPortalClient(String baseUrl, Duration timeout, String fixedCookie, long minRefreshMillis) {
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.timeout = timeout;
		this.fixedCookie = fixedCookie == null ? "" : fixedCookie.trim();
		this.minRefreshMillis = minRefreshMillis;
		this.http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(timeout)
				.followRedirects(HttpClient.Redirect.NORMAL).build();
	}

	/**
	 * Dropdown of the mandals of a district (district id as the portal numbers
	 * them, e.g. 10).
	 */
	public String mandalOptionsHtml(int districtId) {
		return optionsHtml("/getMandalFromDivisionCitizenPortal?district=" + districtId);
	}

	/**
	 * Dropdown of the villages of a mandal (mandal id as the portal numbers them,
	 * e.g. 205).
	 */
	public String villageOptionsHtml(int mandalId) {
		return optionsHtml("/getVillageFromMandalCitizenPortal?mandalId=" + mandalId);
	}

	/** Dropdown of all survey numbers of a village (village id such as 1020008). */
	public String surveyOptionsHtml(String villageId) {
		return optionsHtml("/getSurveyCitizen?villId=" + encode(villageId) + "&flag=survey");
	}

	/** Dropdown of the khata numbers of one survey number of a village. */
	public String khataOptionsHtml(String villageId, String surveyNo) {
		return optionsHtml(
				"/getKhataNoCitizen?villId=" + encode(villageId) + "&flag=khatanos&surveyNo=" + encode(surveyNo));
	}

	private String optionsHtml(String pathAndQuery) {
		String url = baseUrl + pathAndQuery;
		int generation = ensureSession(-1);
		HttpResponse<String> response = send(request(url, true));
		if (isOptionsAnswer(response)) {
			return response.body();
		}
		if (response.statusCode() != 200) {
			throw new PortalException("The portal answered HTTP " + response.statusCode());
		}
		// HTTP 200 without a dropdown: the session was most likely lost. Renew it once
		// and ask again.
		ensureSession(generation);
		response = send(request(url, true));
		if (isOptionsAnswer(response)) {
			return response.body();
		}
		throw new PortalException("The portal did not return a dropdown list (HTTP " + response.statusCode() + ")");
	}

	private static boolean isOptionsAnswer(HttpResponse<String> response) {
		return response.statusCode() == 200 && response.body().contains("<option");
	}

	/**
	 * Makes sure a session exists and returns its generation number. Passing the
	 * generation a caller has seen fail asks for a new session, but only if nobody
	 * else has renewed it in the meantime (and not more often than every few
	 * seconds), so many parallel requests never cause a flood of sessions.
	 */
	private int ensureSession(int failedGeneration) {
		synchronized (sessionLock) {
			if (!fixedCookie.isEmpty()) {
				return 0; // a fixed cookie was configured: use it as is
			}
			long now = System.currentTimeMillis();
			boolean first = sessionStartedAt == 0;
			boolean expired = now - sessionStartedAt > SESSION_MAX_AGE_MILLIS;
			boolean renewalWanted = failedGeneration == sessionGeneration && now - sessionStartedAt >= minRefreshMillis;
			if (first || expired || renewalWanted) {
				cookies.getCookieStore().removeAll();
				send(request(baseUrl + "/knowLandStatus", false)); // the portal sets JSESSIONID here
				sessionStartedAt = now;
				sessionGeneration++;
			}
			return sessionGeneration;
		}
	}

	private HttpRequest request(String url, boolean ajax) {
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET()
				.header("User-Agent", USER_AGENT).header("Accept-Language", "en-US,en;q=0.9");
		if (ajax) {
			b.header("Accept", "*/*").header("Content-Type", "application/x-www-form-urlencoded")
					.header("Referer", baseUrl + "/knowLandStatus").header("X-Requested-With", "XMLHttpRequest");
		} else {
			b.header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8");
		}
		if (!fixedCookie.isEmpty()) {
			b.header("Cookie", fixedCookie);
		}
		return b.build();
	}

	private HttpResponse<String> send(HttpRequest request) {
		try {
			return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new PortalException("Could not reach the portal: " + e, e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new PortalException("Interrupted while calling the portal", e);
		}
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
