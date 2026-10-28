package com.example.dharani.masterdata.copilot;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Mandal master data of one district, in the JSON shape returned by the API. */
public record MandalMaster(Metadata metadata, List<Mandal> mandals) {

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Metadata(String description, String source, String entity, String purpose, String district,
			int districtId, String lastUpdated, int totalMandals) {
	}

	/** {@code name} is the English name, {@code telugu} the Telugu name, exactly as the portal lists them. */
	public record Mandal(int id, String name, String telugu) {
	}
}
