package com.example.dharani.client;

import java.net.URI;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.dharani.config.DharaniProperties;

@Component
public class DharaniClient {

    private final RestClient restClient;
    private final DharaniProperties props;

    public DharaniClient(RestClient dharaniRestClient, DharaniProperties props) {
        this.restClient = dharaniRestClient;
        this.props = props;
    }

    /** Calls the portal for one survey number and returns the raw HTML. */
    public String fetchOptionsHtml(String surveyNo) {
        URI uri = UriComponentsBuilder.fromUriString(props.baseUrl())
                .queryParam("villId", "{villId}")
                .queryParam("flag", "{flag}")
                .queryParam("surveyNo", "{surveyNo}")
                .encode()
                .buildAndExpand(props.villId(), props.flag(), surveyNo)
                .toUri();

        String cookieHeader = props.cookies().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; "));

        return restClient.get()
                .uri(uri)
                .header(HttpHeaders.ACCEPT, "*/*")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .header(HttpHeaders.COOKIE, cookieHeader)
                .header(HttpHeaders.REFERER, props.referer())
                .header(HttpHeaders.USER_AGENT, props.userAgent())
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-origin")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("sec-ch-ua", "\"Chromium\";v=\"154\", \"Google Chrome\";v=\"154\", \"Not A(Brand\";v=\"99\"")
                .header("sec-ch-ua-mobile", "?0")
                .header("sec-ch-ua-platform", "\"Windows\"")
                .retrieve()
                .body(String.class);
    }
}