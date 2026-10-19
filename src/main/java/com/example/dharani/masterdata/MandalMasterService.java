package com.example.dharani.masterdata;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.dharani.masterdata.MandalMaster.Mandal;
import com.example.dharani.masterdata.MandalMaster.Metadata;

/**
 * Mandal master data per district. The list hardly ever changes, so it is cached in memory
 * (bhubharati.mandals.cache-ttl, default 6h); ask with refresh=true to read it from the portal again.
 */
@Service("bhubharatiMandalService")
public class MandalMasterService {

	/** District names for the portal's district ids; add more as you need them. */
	private static final Map<Integer, String> KNOWN_DISTRICTS = Map.of(10, "Siddipet");
	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	private record Cached(List<Mandal> mandals, long fetchedAtMillis, String lastUpdated) {
	}

	private final BhubharatiPortalClient client;
	private final Duration cacheTtl;
	private final Clock clock;
	private final Map<Integer, Cached> cache = new ConcurrentHashMap<>();

	@Autowired
	public MandalMasterService(BhubharatiPortalClient client,
			@Value("${bhubharati.mandals.cache-ttl:6h}") Duration cacheTtl) {
		this(client, cacheTtl, Clock.system(IST));
	}

	MandalMasterService(BhubharatiPortalClient client, Duration cacheTtl, Clock clock) {
		this.client = client;
		this.cacheTtl = cacheTtl;
		this.clock = clock;
	}

	/**
	 * @param districtName optional display name; when null the built-in name for the id is used (if known)
	 * @param refresh      true = ignore the cache and read the portal again
	 */
	public MandalMaster get(int districtId, String districtName, boolean refresh) {
		Cached cached = cache.get(districtId);
		if (refresh || cached == null || isExpired(cached)) {
			cached = load(districtId, cached);
		}
		String district = districtName != null && !districtName.isBlank() ? districtName.trim()
				: KNOWN_DISTRICTS.get(districtId);
		String label = district == null ? "" : district + " ";
		Metadata metadata = new Metadata(label + "Mandal Master Data", "Bhubharati", "Mandal",
				"Mandal dropdown values used in Bhubharati APIs", district, districtId, cached.lastUpdated(),
				cached.mandals().size());
		return new MandalMaster(metadata, cached.mandals());
	}

	private boolean isExpired(Cached cached) {
		return clock.millis() - cached.fetchedAtMillis() > cacheTtl.toMillis();
	}

	/**
	 * One reader at a time. {@code seen} is the cache entry the caller found; if another request has replaced it while
	 * this one was waiting, that newer entry is used, so many simultaneous requests cause only one call to the portal.
	 */
	private synchronized Cached load(int districtId, Cached seen) {
		Cached current = cache.get(districtId);
		if (current != null && current != seen && !isExpired(current)) {
			return current;
		}
		List<Mandal> mandals = List.copyOf(MandalHtmlParser.parse(client.mandalOptionsHtml(districtId)));
		if (mandals.isEmpty()) {
			throw new PortalException("The portal returned no mandals for district " + districtId);
		}
		Cached fresh = new Cached(mandals, clock.millis(), LocalDate.now(clock).format(DATE));
		cache.put(districtId, fresh);
		return fresh;
	}
}
