package com.example.dharani.masterdata.claud.village;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.dharani.masterdata.claud.portal.BhubharatiPortalClient;
import com.example.dharani.masterdata.claud.portal.HtmlOptions;
import com.example.dharani.masterdata.claud.portal.PortalException;
import com.example.dharani.masterdata.claud.village.VillageMaster.Metadata;
import com.example.dharani.masterdata.claud.village.VillageMaster.Village;

/**
 * Village master data per mandal. The list hardly ever changes, so it is cached
 * in memory (bhubharati.villages.cache-ttl, default 6h); ask with refresh=true
 * to read it from the portal again.
 */
@Service("bhubharatiVillageService")
public class VillageMasterService {

	/**
	 * Mandal names for the portal's mandal ids; add more as you need them (or pass
	 * mandalName).
	 */
	private static final Map<Integer, String> KNOWN_MANDALS = Map.of(205, "Siddipet (Urban)", 206, "Siddipet Rural");
	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	private final BhubharatiPortalClient client;
	private final Clock clock;

	@Autowired
	public VillageMasterService(BhubharatiPortalClient client) {
		this.client = client;
		this.clock = Clock.system(IST);
	}

	/**
	 * @param mandalName optional display name; when null the built-in name for the
	 *                   id is used (if known)
	 * @param refresh    true = ignore the cache and read the portal again
	 */
	public VillageMaster get(int mandalId, String mandalName, boolean refresh) {

		List<Village> villages = parse(client.villageOptionsHtml(mandalId));

		if (villages.isEmpty()) {
			throw new PortalException("The portal returned no villages for mandal " + mandalId);
		}
		String mandal = mandalName != null && !mandalName.isBlank() ? mandalName.trim() : KNOWN_MANDALS.get(mandalId);
		String label = mandal == null ? "" : mandal + " ";
		Metadata metadata = new Metadata(label + "Village Master Data", "Bhubharati", "Village",
				"Village dropdown values used in Bhubharati APIs", mandal, mandalId, LocalDate.now(clock).format(DATE),
				villages.size());
		return new VillageMaster(metadata, villages);
	}

	/**
	 * "1019004" + "Mittapalle|మిట్టపల్లి" -> Village(1019004, "Mittapalle",
	 * "మిట్టపల్లి").
	 */
	static List<Village> parse(String html) {
		List<Village> villages = new ArrayList<>();
		for (HtmlOptions.Option option : HtmlOptions.parse(html)) {
			long id;
			try {
				id = Long.parseLong(option.value());
			} catch (NumberFormatException e) {
				continue; // not a village entry
			}
			int bar = option.label().indexOf('|');
			String english = (bar >= 0 ? option.label().substring(0, bar) : option.label()).trim();
			String telugu = (bar >= 0 ? option.label().substring(bar + 1) : "").trim();
			villages.add(new Village(id, english, telugu));
		}
		return List.copyOf(villages);
	}
}
