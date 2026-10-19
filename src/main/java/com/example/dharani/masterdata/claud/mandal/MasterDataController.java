package com.example.dharani.masterdata.claud.mandal;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.dharani.publicdata.model.PublicDataResponse;
import com.example.dharani.service.KhataResult;
import com.example.dharani.service.KhataService;

/**
 * GET /api/bhubharati/mandals?district=10                          -> mandals of district 10 as JSON
 * GET /api/bhubharati/mandals?district=10&districtName=Siddipet    -> same, with an explicit district name
 * GET /api/bhubharati/mandals?district=10&refresh=true             -> read the portal again (skip the cache)
 */
@RestController("claudbhubharatiMandalController")
@RequestMapping("/api/bhu_bharati")
public class MasterDataController {

	private final MasterDataService masterDataservice;
	private final KhataService khataService;

	public MasterDataController(MasterDataService masterDataservice, KhataService khataService) {
		this.masterDataservice = masterDataservice;
		this.khataService = khataService;
	}
	
	/**
	 * Retrieves mandal data for a district in HTML format.
	 *
	 * @param districtId the ID of the district
	 * @return the portal response containing mandal HTML data
	 */
	@GetMapping("/mandal/html")
	public PublicDataResponse mandalsAsHtml(@RequestParam(name = "districtId") Integer districtId) {
		return masterDataservice.fetchMandalsAsHtml(districtId);
	}

	/**
	 * Retrieves mandal master data for a district in JSON format.
	 *
	 * @param districtId the ID of the district
	 * @return mandal metadata and a list of mandal items
	 * 
	 */
	@GetMapping("/mandal/json")
	public MasterData mandalsAsJson(@RequestParam(name = "districtId") Integer districtId) {
		return masterDataservice.fetchMandalsAsJson(districtId);
	}

	/**
	 * Retrieves village data for a mandal in HTML format.
	 *
	 * @param mandalId the ID of the mandal
	 * @return the portal response containing village HTML data
	 */
	@GetMapping("/village/html")
	public PublicDataResponse villagesAsHtml(@RequestParam(name = "mandalId") Integer mandalId) {
		return masterDataservice.fetchVillagesAsHtml(mandalId);
	}

	/**
	 * Retrieves village master data for a mandal in JSON format.
	 *
	 * @param mandalId the ID of the mandal
	 * @return village metadata and a list of village items
	 */
	@GetMapping("/village/json")
	public MasterData villagesAsJson(@RequestParam(name = "mandalId") Integer mandalId) {
		return masterDataservice.fetchVillagesAsJson(mandalId);
	}

	/**
	 * Retrieves survey numbers for a village in JSON format.
	 *
	 * @param villageId the ID of the village
	 * @return a list of survey numbers associated with the village
	 */
	@GetMapping("/surveyNo/json")
	public List<String> getVillageSurveyNosAsJson(@RequestParam(name = "villageId") Integer villageId) {
		return masterDataservice.fetchVillageSurveyNosAsJson(villageId);
	}

	/**
	 * Retrieves survey numbers for a village in HTML format.
	 *
	 * @param villageId the ID of the village
	 * @return the portal response containing survey-number HTML data
	 */
	@GetMapping("/surveyNo/html")
	public PublicDataResponse getVillageSurveyNosAsHtml(@RequestParam(name = "villageId") Integer villageId) {
		return masterDataservice.fetchVillageSurveyNosAsHtml(villageId);
	}

	/**
	 * Retrieves survey numbers and their associated khata numbers for a village.
	 *
	 * @param villageId the ID of the village
	 * @return a list of survey-number and khata-number results
	 */
	@GetMapping("/survey_khataNo/json")
	public List<KhataResult> getVillageSurveyNosWithKhataNosAsJson(
			@RequestParam(name = "villageId") Integer villageId) {
		return masterDataservice.getVillageSurveyNosWithKhataNosAsJson(villageId);
	}

	/** Runs all survey numbers & khata-number of every line in survey-numbers.txt. file */
	@GetMapping("/khata-numbers")
	public List<KhataResult> all() {
		return khataService.fetchAllFromFile();
	}

	/** Test a single survey number: /api/khata-numbers/single?surveyNo=859/2 */
	@GetMapping("/khata-numbers/single")
	public KhataResult single(@RequestParam("surveyNo") String surveyNo,
			@RequestParam(name = "villageId", required = false) String villageId) {
		return khataService.fetchOne(surveyNo, villageId);
	}
}
