package com.example.dharani.masterdata.copilot;

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

/**
 * GET /api/bhubharati/mandals?district=10                          -> mandals of district 10 as JSON
 * GET /api/bhubharati/mandals?district=10&districtName=Siddipet    -> same, with an explicit district name
 * GET /api/bhubharati/mandals?district=10&refresh=true             -> read the portal again (skip the cache)
 */
@RestController("bhubharatiMandalController")
@RequestMapping("/api/bhubharati")
public class MandalController {

	private static final Logger log = LoggerFactory.getLogger(MandalController.class);
	private static final Pattern DISTRICT_NAME = Pattern.compile("^[A-Za-z][A-Za-z .()-]{0,39}$");

	private final MandalMasterService service;

	public MandalController(MandalMasterService service) {
		this.service = service;
	}

	@GetMapping("/mandals")
	public ResponseEntity<MandalMaster> mandals(@RequestParam(name = "district") Integer district,
			@RequestParam(name = "districtName", required = false) String districtName,
			@RequestParam(name = "refresh", defaultValue = "false") boolean refresh) {

		if (district == null || district < 1 || district > 999) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "district must be a number between 1 and 999");
		}
		if (districtName != null && !districtName.isBlank() && !DISTRICT_NAME.matcher(districtName.trim()).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "districtName contains invalid characters");
		}
		try {
			return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
					.body(service.get(district, districtName, refresh));
		} catch (PortalException e) {
			log.warn("Bhubharati portal problem for district {}: {}", district, e.getMessage());
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
					"The Bhubharati portal did not return mandal data. Try again shortly.", e);
		}
	}
}
