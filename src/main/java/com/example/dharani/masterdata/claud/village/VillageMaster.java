package com.example.dharani.masterdata.claud.village;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Village master data of one mandal, in the JSON shape returned by the API. */
public record VillageMaster(Metadata metadata, List<Village> villages) {

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Metadata(String description, String source, String entity, String purpose, String mandal,
			int mandalId, String lastUpdated, int totalVillages) {
	}

	/**
	 * {@code id} is the village id used by the other portal calls, e.g. 1019004 for
	 * Mittapalle.
	 */
	public record Village(long id, String name, String telugu) {
	}
}
