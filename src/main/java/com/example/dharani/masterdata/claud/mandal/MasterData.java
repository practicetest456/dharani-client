package com.example.dharani.masterdata.claud.mandal;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Master data with metadata and a list of items.
 */
public record MasterData(Metadata metadata, List<Item> items) {

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Metadata(String description, String source, String entity, String purpose, String parentName,
			int parentId, String lastUpdated, int totalItems) {
	}

	/**
	 * A master-data item such as a district, mandal, or village.
	 */
	public record Item(long id, String name, String telugu) {
	}
}