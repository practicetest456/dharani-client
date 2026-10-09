package com.example.dharani.publicdata.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.dharani.publicdata.client.PublicDataClient;
import com.example.dharani.publicdata.config.PublicDataProperties;
import com.example.dharani.publicdata.model.LandDetailsResult;
import com.example.dharani.publicdata.model.PublicDataRequest;
import com.example.dharani.publicdata.model.PublicDataResponse;

@Service
public class PublicDataService {

    private final PublicDataClient client;
    private final LandDetailsParser parser;
    private final LandDetailsHtmlRenderer renderer;
    private final PublicDataProperties defaults;

    public PublicDataService(PublicDataClient client, LandDetailsParser parser,
                             LandDetailsHtmlRenderer renderer, PublicDataProperties defaults) {
        this.client = client;
        this.parser = parser;
        this.renderer = renderer;
        this.defaults = defaults;
    }

    /** Raw response. Any field left empty in the request is taken from application.yml. */
    public PublicDataResponse fetch(PublicDataRequest r) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("villageId", nz(r.villageId(), defaults.villageId()));
        params.put("flagToSearch", nz(r.flagToSearch(), defaults.flagToSearch()));
        params.put("searchData", nz(r.searchData(), defaults.searchData()));
        params.put("flagval", nz(r.flagval(), defaults.flagval()));
        params.put("district", nz(r.district(), defaults.district()));
        params.put("mandal", nz(r.mandal(), defaults.mandal()));
        params.put("divi", nz(r.divi(), defaults.divi()));
        params.put("khataNoIdselect", nz(r.khataNoIdselect(), defaults.khataNoIdselect()));
        params.put("ReqType", nz(r.reqType(), defaults.reqType()));
        params.put("captcha", nz(r.captcha(), defaults.captcha()));
        return client.post(params);
    }

    public LandDetailsResult fetchParsed(PublicDataRequest r) {
        return parser.parse(fetch(r).body());
    }

    public String fetchHtmlView(PublicDataRequest r) {
        return renderer.render(fetchParsed(r));
    }

    private static String nz(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value;
        }
        return fallback == null ? "" : fallback;
    }
}