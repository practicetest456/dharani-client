package com.example.dharani.publicdata.client;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.example.dharani.config.DharaniProperties;
import com.example.dharani.publicdata.model.PublicDataResponse;

@Component
public class PublicDataClient {

    private static final String URL = "https://bhubharati.telangana.gov.in/getPublicDataInfo";
    private static final String ORIGIN = "https://bhubharati.telangana.gov.in";
    private static final String REFERER = "https://bhubharati.telangana.gov.in/knowLandStatus";

    private final RestClient restClient;
    private final DharaniProperties props; // reused only for cookies + user-agent

    public PublicDataClient(RestClient restClient, DharaniProperties props) {
        this.restClient = restClient;
        this.props = props;
    }

    /** POST getPublicDataInfo?<params> with an empty body, like the browser does. */
    /**
     * example request from bubharathi is below
     * https://bhubharati.telangana.gov.in/getPublicDataInfo?villageId=1019004&flagToSearch=surveynumber&searchData=zNPu7s8elCxREc1fF6iihg%3D%3D&flagval=district&district=10&mandal=205&divi=&khataNoIdselect=1005&ReqType=Department&captcha=jXeeT67/4WVaUry9pCEf5Q==
     * @param params
     * @return
     */
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

			// Uncomment to print the request as cURL for debugging or Postman testing.
            // extractCurlForPostMethod(uri);

            MediaType ct = res.getHeaders().getContentType();
            return new PublicDataResponse(res.getStatusCode().value(),
                    ct == null ? null : ct.toString(), utf8(res.getBody()));
        } catch (RestClientResponseException e) {
            return new PublicDataResponse(e.getStatusCode().value(), null,
                    utf8(e.getResponseBodyAsByteArray()));
        }
    }
    
    
    /**
     * GET getPublicDataInfo with query parameters.
     */
    public PublicDataResponse get(String url,Map<String, String> params) {
		
		URI uri;
		
		if (params != null) {
			String query = params.entrySet().stream().map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
					.collect(Collectors.joining("&"));

			uri = URI.create(url + "?" + query);
		} else {
			uri = URI.create(url);
		}
		
		try {
            ResponseEntity<byte[]> res = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.ACCEPT, "*/*")
                    .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9")
                    .header(HttpHeaders.COOKIE, cookieHeader())
                    .header(HttpHeaders.ORIGIN, ORIGIN)
                    .header(HttpHeaders.REFERER, REFERER)
                    .header(HttpHeaders.USER_AGENT, props.userAgent())
                    .header("X-Requested-With", "XMLHttpRequest")
                    .retrieve()
                    .toEntity(byte[].class);

            MediaType ct = res.getHeaders().getContentType();

            return new PublicDataResponse(
                    res.getStatusCode().value(),
                    ct == null ? null : ct.toString(),
                    utf8(res.getBody()));

        } catch (ResourceAccessException e) {
            throw new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "Bhubharati portal request timed out. Please try again.",
                    e
            );
        } catch (RestClientResponseException e) {
            return new PublicDataResponse(
                    e.getStatusCode().value(),
                    null,
                    utf8(e.getResponseBodyAsByteArray()));
        }
    }

	private void extractCurlForPostMethod(URI uri) {
		String curl = """
				curl --location --request POST '%s' \\
				--header 'X-Requested-With: XMLHttpRequest' \\
				--header 'Content-Type: application/x-www-form-urlencoded' \\
				--header 'Accept: */*' \\
				--header 'Accept-Language: en-US,en;q=0.9' \\
				--header 'Origin: %s' \\
				--header 'Referer: %s' \\
				--header 'User-Agent: %s' \\
				--header 'Cookie: %s'
				""".formatted(uri, ORIGIN, REFERER, props.userAgent(), cookieHeader());

		System.out.println("\n========== CURL REQUEST ==========");
		System.out.println(curl);
		System.out.println("==================================\n");
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