package com.example.dharani.masterdata.claud.common;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import com.example.dharani.masterdata.claud.mandal.MasterData;
import com.example.dharani.masterdata.claud.mandal.MasterData.Item;

@Component
public class MasterDataParser {

	/**
	 * Parses Mandal/Village master data.
	 *
	 * Example: <option value="205">Siddipet | సిద్దిపేట</option>
	 */
	public MasterData parse(String html) {

		Document doc = Jsoup.parse(html == null ? "" : html);
		List<Item> items = new ArrayList<>();

		for (Element option : doc.select("option")) {

			long id;

			try {
				id = Long.parseLong(option.attr("value"));
			} catch (NumberFormatException e) {
				continue;
			}

			if (id <= 0) {
				continue;
			}

			String label = option.text().trim();
			int separator = label.indexOf('|');

			String english = (separator >= 0 ? label.substring(0, separator) : label).trim();

			String telugu = (separator >= 0 ? label.substring(separator + 1) : "").trim();

			items.add(new Item(id, english, telugu));
		}

		return new MasterData(null, List.copyOf(items));
	}

	/**
	 * Parses survey numbers.
	 *
	 * Extracts only the value attribute.
	 *
	 * Input: <option value="0">Please select</option>
	 * <option value="1/అ/1">1/అ/1</option> <option value="2/ఆ/2">2/ఆ/2</option>
	 *
	 * Output: ["0", "1/అ/1", "2/ఆ/2"]
	 *
	 * No value is intentionally skipped.
	 */
	public List<String> parseSurveyNumbers(String html) {

		if (html == null || html.isEmpty()) {
			return List.of();
		}

		Document doc = Jsoup.parse(html);

		var options = doc.getElementsByTag("option");

		List<String> surveyNumbers = new ArrayList<>(options.size());

		for (Element option : options) {
			surveyNumbers.add(option.attr("value"));
		}

		return surveyNumbers;
	}
}