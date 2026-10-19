package com.example.dharani.publicdata.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import com.example.dharani.common.model.Ror1BLandDetails;

@Component
public class LandDetailsParser {

    public Ror1BLandDetails parse(String html) {
        Document doc = Jsoup.parse(html == null ? "" : html);
        return new Ror1BLandDetails(
                section(doc, "Pattadar Details"),
                section(doc, "Land Details"));
    }

    /** Finds the <h4> with the given text, then reads every <strong>label</strong> value pair in its card. */
    private Map<String, String> section(Document doc, String heading) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Element h4 : doc.select("h4.heading-details")) {
            if (!h4.text().trim().equalsIgnoreCase(heading)) {
                continue;
            }
            Element card = h4.nextElementSibling();
            if (card == null) {
                continue;
            }
            for (Element strong : card.select("strong")) {
                Element cell = strong.parent();
                String label = strong.text().trim();
                String value = cell == null ? "" : cell.ownText().trim();
                map.put(label, value);
            }
        }
        return map;
    }
}