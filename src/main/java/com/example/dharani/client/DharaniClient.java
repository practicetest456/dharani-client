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

    public DharaniClient(RestClient restClient, DharaniProperties props) {
        this.restClient = restClient;
        this.props = props;
    }

    /** Calls the portal for one survey number and returns the raw HTML. */
    public String fetchOptionsHtml(String surveyNo, String villId) {
    	
    	// Use provided villId.
        // If not provided, use the default villId from properties.
        String finalVillId =
                villId != null && !villId.isBlank()
                        ? villId
                        : props.villId();
        URI uri = UriComponentsBuilder.fromUriString(props.baseUrl())
                .queryParam("villId", "{villId}")
                .queryParam("flag", "{flag}")
                .queryParam("surveyNo", "{surveyNo}")
                .encode()
                .buildAndExpand(finalVillId, props.flag(), surveyNo)
                .toUri();

        String cookieHeader = props.cookies().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("; "));

		// Uncomment to print the request as cURL for debugging or Postman testing.
		// extractCurlForfetchFromHtml(uri, cookieHeader);
   
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
    
	private void extractCurlForfetchFromHtml(URI uri, String cookieHeader) {
		String curl = """
				curl --location --request GET '%s' \\
				--header 'Accept: */*' \\
				--header 'Accept-Language: en-US,en;q=0.9' \\
				--header 'Content-Type: application/x-www-form-urlencoded' \\
				--header 'Cookie: %s' \\
				--header 'Referer: %s' \\
				--header 'User-Agent: %s' \\
				--header 'Sec-Fetch-Dest: empty' \\
				--header 'Sec-Fetch-Mode: cors' \\
				--header 'Sec-Fetch-Site: same-origin' \\
				--header 'X-Requested-With: XMLHttpRequest' \\
				--header 'sec-ch-ua: "Chromium";v="154", "Google Chrome";v="154", "Not A(Brand";v="99"' \\
				--header 'sec-ch-ua-mobile: ?0' \\
				--header 'sec-ch-ua-platform: "Windows"'
				""".formatted(uri, cookieHeader, props.referer(), props.userAgent());

		System.out.println("\n========== CURL REQUEST ==========");
		System.out.println(curl);
		System.out.println("==================================\n");
	}
}