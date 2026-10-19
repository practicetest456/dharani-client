package com.example.dharani.masterdata.claud.mandal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.dharani.masterdata.claud.common.MasterDataParser;
import com.example.dharani.publicdata.client.PublicDataClient;
import com.example.dharani.publicdata.model.PublicDataResponse;
import com.example.dharani.service.KhataResult;
import com.example.dharani.service.KhataService;

/**
 * Mandal master data per district.
 */
@Service("claudbhubharatiMandalService")
public class MasterDataService {

	private static final String GET_MANDAL_URL = "https://bhubharati.telangana.gov.in/getMandalFromDivisionCitizenPortal";
	private static final String GET_VILLAGE_URL = "https://bhubharati.telangana.gov.in/getVillageFromMandalCitizenPortal";
	private static final String GET_SURVEY_NOS_URL = "https://bhubharati.telangana.gov.in/getSurveyCitizen";

	private PublicDataClient publicDataClient;
	private MasterDataParser mandalParser;
	private final KhataService khataService;

	public MasterDataService(PublicDataClient publicDataClient, MasterDataParser mandalParser,
			KhataService khataService) {
		this.publicDataClient = publicDataClient;
		this.mandalParser = mandalParser;
		this.khataService = khataService;
	}

	public MasterData fetchMandalsAsJson(Integer districtId) {
		return mandalParser.parse(fetchMandalsAsHtml(districtId).body());
	}

	public PublicDataResponse fetchMandalsAsHtml(Integer districtId) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("district", districtId.toString());
		return publicDataClient.get(GET_MANDAL_URL, params);
	}

	public MasterData fetchVillagesAsJson(Integer mandalId) {
		return mandalParser.parse(fetchVillagesAsHtml(mandalId).body());
	}

	public PublicDataResponse fetchVillagesAsHtml(Integer mandalId) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("mandalId", mandalId.toString());
		return publicDataClient.get(GET_VILLAGE_URL, params);
	}

	public List<String> fetchVillageSurveyNosAsJson(Integer villageId) {
		return mandalParser.parseSurveyNumbers(fetchVillageSurveyNosAsHtml(villageId).body());
	}

	public PublicDataResponse fetchVillageSurveyNosAsHtml(Integer villageId) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("villId", villageId.toString());
		params.put("flag", "survey");
		return publicDataClient.get(GET_SURVEY_NOS_URL, params);
	}

	public List<KhataResult> getVillageSurveyNosWithKhataNosAsJson(Integer villageId) {
		List<KhataResult> khataResultList = khataService.fetchSurveyKhataNos(fetchVillageSurveyNosAsJson(villageId),
				villageId.toString());
		return khataResultList;
	}
}
