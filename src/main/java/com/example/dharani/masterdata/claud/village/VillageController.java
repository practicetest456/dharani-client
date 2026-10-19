package com.example.dharani.masterdata.claud.village;

import java.time.Duration;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.dharani.masterdata.claud.portal.PortalException;

/**
 * GET /api/bhubharati/villages?mandalId=205                            -> villages of mandal 205 as JSON
 * GET /api/bhubharati/villages?mandalId=205&mandalName=Siddipet (Urban) -> same, with an explicit mandal name
 * GET /api/bhubharati/villages?mandalId=205&refresh=true               -> read the portal again (skip the cache)
 */
@RestController("bhubharatiVillageController")
@RequestMapping("/api/bhubharati")
public class VillageController {

	private static final Logger log = LoggerFactory.getLogger(VillageController.class);
	private static final Pattern MANDAL_NAME = Pattern.compile("^[A-Za-z][A-Za-z .()\\[\\]-]{0,39}$");

	private final VillageMasterService service;

	public VillageController(VillageMasterService service) {
		this.service = service;
	}

	@GetMapping("/villages")
	public ResponseEntity<VillageMaster> villages(@RequestParam(name = "mandalId") Integer mandalId,
			@RequestParam(name = "mandalName", required = false) String mandalName,
			@RequestParam(name = "refresh", defaultValue = "false") boolean refresh) {

		if (mandalId == null || mandalId < 1 || mandalId > 99999) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mandalId must be a number between 1 and 99999");
		}
		if (mandalName != null && !mandalName.isBlank() && !MANDAL_NAME.matcher(mandalName.trim()).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mandalName contains invalid characters");
		}
		try {
			return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
					.body(service.get(mandalId, mandalName, refresh));
		} catch (PortalException e) {
			log.warn("Bhubharati portal problem for mandal {}: {}", mandalId, e.getMessage());
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
					"The Bhubharati portal did not return village data. Try again shortly.", e);
		}
	}
}
