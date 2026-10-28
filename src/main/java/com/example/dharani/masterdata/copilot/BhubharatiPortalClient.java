package com.example.dharani.masterdata.copilot;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Calls the Bhubharati citizen portal the way its own web page does. It first opens the "Know Land Status" page to
 * get a fresh session cookie (so no cookie has to be copied from a browser, and an expired session fixes itself),
 * then asks for the dropdown fragment.
 *
 * Uses only the JDK HTTP client. Optional settings: bhubharati.base-url, bhubharati.timeout (default 15s) and
 * bhubharati.cookie (a fixed Cookie header, only if the portal ever refuses the automatic session).
 */
@Component("bhubharatiPortalClient")
public class BhubharatiPortalClient {

	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
			+ "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";
	private static final long SESSION_MAX_AGE_MILLIS = 15 * 60 * 1000L;

	private final String baseUrl;
	private final Duration timeout;
	private final String fixedCookie;
	private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
	private final HttpClient http;
	private long sessionStartedAt; // guarded by this

	public BhubharatiPortalClient(
			@Value("${bhubharati.base-url:https://bhubharati.telangana.gov.in}") String baseUrl,
			@Value("${bhubharati.timeout:15s}") Duration timeout, @Value("${bhubharati.cookie:}") String fixedCookie) {
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.timeout = timeout;
		this.fixedCookie = fixedCookie == null ? "" : fixedCookie.trim();
		this.http = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(timeout)
				.followRedirects(HttpClient.Redirect.NORMAL).build();
	}

	/** The HTML {@code <option>} list of the mandals of a district. */
	public String mandalOptionsHtml(int districtId) {
		String url = baseUrl + "/getMandalFromDivisionCitizenPortal?district=" + districtId;
		for (int attempt = 1; attempt <= 2; attempt++) {
			ensureSession(attempt == 2); // second try: start over with a brand-new session
			HttpResponse<String> response = send(request(url, true));
			if (response.statusCode() == 200 && response.body().contains("<option")) {
				return response.body();
			}
		}
		throw new PortalException("The portal did not return a mandal list for district " + districtId);
	}

	private synchronized void ensureSession(boolean force) {
		if (!fixedCookie.isEmpty()) {
			return; // a fixed cookie was configured: use it as is
		}
		long now = System.currentTimeMillis();
		if (!force && sessionStartedAt != 0 && now - sessionStartedAt < SESSION_MAX_AGE_MILLIS) {
			return;
		}
		cookies.getCookieStore().removeAll();
		send(request(baseUrl + "/knowLandStatus", false)); // the portal sets JSESSIONID here
		sessionStartedAt = now;
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
}
