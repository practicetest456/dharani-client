package com.example.dharani.common.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One pattadar + land entry exactly as delivered in the portal JSON. Immutable
 * and null-safe.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Ror1BLandDetails(Map<String, String> pattadarDetails, Map<String, String> landDetails) {

	public Ror1BLandDetails {
		pattadarDetails = immutable(pattadarDetails);
		landDetails = immutable(landDetails);
	}

	public String khataNumber() {
		return pattadarDetails.get("Khata Number");
	}

	public String surveyNo() {
		return landDetails.get("Survey No./ Sub-Division No.");
	}

	private static Map<String, String> immutable(Map<String, String> source) {
		return source == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(source));
	}
	
	public boolean isEmpty() {
        return pattadarDetails.isEmpty() && landDetails.isEmpty();
    }
}
