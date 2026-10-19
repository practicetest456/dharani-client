package com.example.dharani.publicdata.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.dharani.common.model.Ror1BLandDetails;
import com.example.dharani.publicdata.model.PublicDataRequest;
import com.example.dharani.publicdata.model.PublicDataResponse;
import com.example.dharani.publicdata.service.PublicDataService;

@RestController
@RequestMapping("/api/public-data")
public class PublicDataInfoController {

	private static final String HTML = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8";

	private final PublicDataService service;

	public PublicDataInfoController(PublicDataService service) {
		this.service = service;
	}

	// ---------- simple GET: everything comes from application.yml ----------

	@GetMapping("/info")
	public PublicDataResponse infoGet(@RequestParam Map<String, String> q) {
		return service.fetch(from(q));
	}

	@GetMapping("/parsed")
	public Ror1BLandDetails parsedGet(@RequestParam Map<String, String> q) {
		return service.fetchParsed(from(q));
	}

	@GetMapping(value = "/view", produces = HTML)
	public String viewGet(@RequestParam Map<String, String> q) {
		return service.fetchHtmlView(from(q));
	}

	// ---------- POST with a JSON body (optional, still works) ----------

	@PostMapping("/info")
	public PublicDataResponse info(@RequestBody PublicDataRequest request) {
		return service.fetch(request);
	}

	@PostMapping("/parsed")
	public Ror1BLandDetails parsed(@RequestBody PublicDataRequest request) {
		return service.fetchParsed(request);
	}

	@PostMapping(value = "/view", produces = HTML)
	public String view(@RequestBody PublicDataRequest request) {
		return service.fetchHtmlView(request);
	}

	/** Optional query params override the yml values. */
	private static PublicDataRequest from(Map<String, String> q) {
		return new PublicDataRequest(q.get("villageId"), q.get("flagToSearch"), q.get("searchData"), q.get("flagval"),
				q.get("district"), q.get("mandal"), q.get("divi"), q.get("khataNoIdselect"), q.get("ReqType"),
				q.get("captcha"));
	}

	@GetMapping("/all-surveys-parsed")
	public List<Ror1BLandDetails> allSurveysParsed() {
		return service.fetchParsedForAllSurveys();
	}
}