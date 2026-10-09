package com.example.dharani.publicdata.client;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.example.dharani.config.DharaniProperties;
import com.example.dharani.publicdata.model.PublicDataResponse;

@Component
public class PublicDataClient {

    private static final String URL = "https://bhubharati.telangana.gov.in/getPublicDataInfo";
    private static final String ORIGIN = "https://bhubharati.telangana.gov.in";
    private static final String REFERER = "https://bhubharati.telangana.gov.in/knowLandStatus";

    private final RestClient restClient;
    private final DharaniProperties props; // reused only for cookies + user-agent

    public PublicDataClient(RestClient dharaniRestClient, DharaniProperties props) {
        this.restClient = dharaniRestClient;
        this.props = props;
    }

    /** POST getPublicDataInfo?<params> with an empty body, like the browser does. */
    public PublicDataResponse post(Map<String, String> params) {
        String query = params.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
        URI uri = URI.create(URL + "?" + query);

        try {
            ResponseEntity<byte[]> res = restClient.post()
                    .uri(uri)
                    .header(HttpHeaders.ACCEPT, "*/*")
                    .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header(HttpHeaders.COOKIE, cookieHeader())
                    .header(HttpHeaders.ORIGIN, ORIGIN)
                    .header(HttpHeaders.REFERER, REFERER)
                    .header(HttpHeaders.USER_AGENT, props.userAgent())
                    .header("X-Requested-With", "XMLHttpRequest")
                    .body("")
                    .retrieve()
                    .toEntity(byte[].class);

            MediaType ct = res.getHeaders().getContentType();
            return new PublicDataResponse(res.getStatusCode().value(),
                    ct == null ? null : ct.toString(), utf8(res.getBody()));
        } catch (RestClientResponseException e) {
            return new PublicDataResponse(e.getStatusCode().value(), null,
                    utf8(e.getResponseBodyAsByteArray()));
        }
    }

    /** The portal sends UTF-8 even though its header/meta says ISO-8859-1. */
    private static String utf8(byte[] bytes) {
        return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
    }

    private String cookieHeader() {
        return props.cookies().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; "));
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}