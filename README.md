# Dharani Client

This repository contains a Spring Boot application that acts as a client for the Telangana Bhubharati / Dharani government portal. In the current source, it does two main things:

- Retrieves khata numbers for a survey number by calling the portal and parsing the returned HTML option list.
- Retrieves land/public-data details for a khata record, parses the HTML response, and exposes either the raw response, a structured JSON-like result, or a rendered HTML view.

The project is intentionally browser-like: it sends cookies, headers, referer values, and a user-agent that mimic a browser session. The configuration file contains the exact values required by the external portal, and these values are loaded through Spring Boot configuration properties.

## Project overview

This is not a general-purpose database or business application. It is a focused integration client for a specific government web service. The code uses:

- Spring Boot for application startup and REST endpoints
- Spring Web `RestClient` for outbound HTTP calls
- Jsoup for HTML parsing
- Java records for DTOs
- configuration properties for portal connection details
- a resource-based survey number file to batch process many records

The project is centered around two domains:

1. Dharani khata lookup
2. Public land-data lookup

The source tree strongly suggests the app is used to automate tasks that were originally performed manually through the Bhubharati site.

## Main features

- Reads survey numbers from a text file
- Queries the portal for khata numbers for one survey number
- Processes many survey numbers concurrently
- Skips empty and placeholder option values
- Saves results to a CSV file
- Supports raw portal response, parsed sections, and rendered HTML output for land details
- Allows overriding configuration values via request query parameters
- Uses browser-like headers and cookies to maintain compatibility with the portal

## Technology stack

- Java 25
- Spring Boot 3.5.6
- Spring Web
- Java HTTP client (`HttpClient`) behind `RestClient`
- Jsoup 1.21.2
- Gradle
- Spring Boot testing starter

The build file currently declares:

- `org.springframework.boot` version `3.5.6`
- Java toolchain version `25`
- `spring-boot-starter-web`
- `org.jsoup:jsoup`
- `spring-boot-starter-test`

## Architecture

The application follows a simple layered architecture:

- Web layer:
  - REST controllers
- Service layer:
  - business logic for fetching and parsing portal data
- Integration layer:
  - outbound client classes for the external portal
- Model layer:
  - request/response records
- Configuration layer:
  - `@ConfigurationProperties` classes and HTTP client setup

This design is visible in the package structure:

- `com.example.dharani.web`
- `com.example.dharani.service`
- `com.example.dharani.client`
- `com.example.dharani.config`
- `com.example.dharani.publicdata.*`

## Package structure

```text
src/main/java/com/example/dharani
├── DharaniClientApplication.java
├── client
│   └── DharaniClient.java
├── config
│   ├── DharaniProperties.java
│   └── RestClientConfig.java
├── publicdata
│   ├── client
│   │   └── PublicDataClient.java
│   ├── config
│   │   └── PublicDataProperties.java
│   ├── controller
│   │   └── PublicDataInfoController.java
│   ├── model
│   │   ├── LandDetailsResult.java
│   │   ├── PublicDataRequest.java
│   │   └── PublicDataResponse.java
│   └── service
│       ├── LandDetailsHtmlRenderer.java
│       ├── LandDetailsParser.java
│       └── PublicDataService.java
├── service
│   ├── KhataResult.java
│   └── KhataService.java
└── web
    └── DharaniController.java
```

## Important classes and their responsibilities

### `DharaniClientApplication`
Bootstraps the Spring Boot application and enables configuration property scanning.

### `DharaniProperties`
Binds all `dharani.*` settings from `application.yml`, including:

- portal base URL
- survey URL
- village ID
- cookie values
- user-agent
- timeouts
- concurrency
- output file name
- survey file path

### `RestClientConfig`
Builds the shared `RestClient` used by the portal integrations. It configures:

- a Java HTTP client
- connection timeout
- read timeout

### `DharaniClient`
Performs the actual HTTP GET to the Bhubharati portal for khata number options.

Responsibilities:
- builds the query string with `villId`, `flag`, and `surveyNo`
- prepares the required HTTP headers
- sends the portal cookies and referer
- retrieves the raw HTML response from the portal

### `KhataService`
Core service for khata-number processing.

Responsibilities:
- reads survey numbers from the configured file
- processes survey numbers in parallel using virtual threads
- retries failed requests up to 2 times
- parses option values from HTML using Jsoup
- returns `KhataResult`
- writes CSV results

### `KhataResult`
A simple record with:

- `surveyNo`
- `khataNos`
- `error`

### `DharaniController`
Exposes the current REST endpoints for khata-number lookups:

- `GET /api/khata-numbers`
- `GET /api/khata-numbers/single`

### `PublicDataProperties`
Binds all `public-data.*` settings from `application.yml` used for land-details queries.

### `PublicDataClient`
Sends POST requests to:

- `https://bhubharati.telangana.gov.in/getPublicDataInfo`

It:
- builds the full query string
- encodes values with UTF-8
- sends browser-like headers
- reuses the session cookies from `DharaniProperties`
- returns a `PublicDataResponse`

### `PublicDataService`
Constructs the request parameter map and resolves a fallback value from configuration when request values are omitted.

### `PublicDataInfoController`
Exposes the public-data endpoints:

- `GET /api/public-data/info`
- `GET /api/public-data/parsed`
- `GET /api/public-data/view`
- `POST /api/public-data/info`
- `POST /api/public-data/parsed`
- `POST /api/public-data/view`

### `LandDetailsParser`
Parses the raw HTML and extracts two maps:

- `pattadarDetails`
- `landDetails`

It looks for headings like:
- `Pattadar Details`
- `Land Details`

### `LandDetailsHtmlRenderer`
Converts parsed land-details data into a readable HTML page styled as a government-style land record.

It:
- builds a printable page
- highlights status-like values
- formats numbers with Indian locale formatting
- renders key values like pattadar name, khata number, survey number, and total extent

### `LandDetailsResult`
A record that contains:

- `pattadarDetails: Map<String, String>`
- `landDetails: Map<String, String>`

It includes `isEmpty()` to detect no-data responses.

## Configuration

The application uses `application.yml` under `src/main/resources`.

Key sections:

- `server.port: 8080`
- `dharani.*` for khata lookup configuration
- `public-data.*` for land details configuration

Important settings from `application.yml` include:

- `dharani.base-url`
- `dharani.survey-url`
- `dharani.vill-id`
- `dharani.flag`
- `dharani.referer`
- `dharani.survey-file`
- `dharani.concurrency`
- `dharani.output-file`
- `dharani.cookies`
- `dharani.user-agent`
- `dharani.connect-timeout`
- `dharani.read-timeout`

For public data:

- `public-data.village-id`
- `public-data.flag-to-search`
- `public-data.search-data`
- `public-data.flagval`
- `public-data.district`
- `public-data.mandal`
- `public-data.divi`
- `public-data.khata-no-idselect`
- `public-data.req-type`
- `public-data.captcha`

The configuration comments in `application.yml` are very specific and indicate that the portal relies on a valid browser session and current cookies. The file even warns that stale `JSESSIONID` values will cause empty results.

## API endpoints

The current source code exposes these endpoints.

### Khata-number endpoints

#### `GET /api/khata-numbers`
Returns a list of `KhataResult` entries for all survey numbers listed in the configured survey file.

Response:
```json
[
  {
    "surveyNo": "859/2",
    "khataNos": ["1001", "1002"],
    "error": null
  }
]
```

#### `GET /api/khata-numbers/single`
Returns the result for one survey number.

Query parameter:
- `surveyNo` (required)

Example:
```text
/api/khata-numbers/single?surveyNo=859/2
```

### Public-data endpoints

#### `GET /api/public-data/info`
Returns the raw `PublicDataResponse` from the portal.

Query parameters:
- `villageId`
- `flagToSearch`
- `searchData`
- `flagval`
- `district`
- `mandal`
- `divi`
- `khataNoIdselect`
- `ReqType`
- `captcha`

Any missing values fall back to the `public-data.*` entries in `application.yml`.

#### `GET /api/public-data/parsed`
Returns a parsed `LandDetailsResult` containing:

- `pattadarDetails`
- `landDetails`

#### `GET /api/public-data/view`
Returns a clean HTML page representing the land details.

It sets `produces = "text/html;charset=UTF-8"`.

#### `POST /api/public-data/info`
Same as the GET version, but with `PublicDataRequest` in JSON body.

#### `POST /api/public-data/parsed`
Same as the GET version, but with `PublicDataRequest` in JSON body.

#### `POST /api/public-data/view`
Same as the GET version, but with `PublicDataRequest` in JSON body.

Note: The repository comments in `application.yml` also mention older or planned endpoints such as:
- `/api/survey-numbers`
- `/api/village-khata`
- `/api/khata-numbers/single` (documented in comments)
- `/api/raw`

Those are described in comments, but the source code present in this repository only implements the `khata-numbers` and `public-data` endpoints above.

## Request examples

### Example 1: Fetch khata numbers for one survey number

```bash
curl "http://localhost:8080/api/khata-numbers/single?surveyNo=859/2"
```

### Example 2: Process all survey numbers from the configured file

```bash
curl "http://localhost:8080/api/khata-numbers"
```

### Example 3: Fetch the raw public-data response

```bash
curl "http://localhost:8080/api/public-data/info?villageId=1019004&flagToSearch=surveynumber&searchData=zNPu7s8elCxREc1fF6iihg==&flagval=district&district=10&mandal=205&divi=&khataNoIdselect=1005&ReqType=Citizen&captcha=jXeeT67/4WVaUry9pCEf5Q=="
```

### Example 4: Fetch parsed land details

```bash
curl "http://localhost:8080/api/public-data/parsed?villageId=1019004&flagToSearch=surveynumber&searchData=zNPu7s8elCxREc1fF6iihg==&flagval=district&district=10&mandal=205&divi=&khataNoIdselect=1005&ReqType=Citizen&captcha=jXeeT67/4WVaUry9pCEf5Q=="
```

### Example 5: Fetch rendered HTML view

```bash
curl "http://localhost:8080/api/public-data/view?villageId=1019004&flagToSearch=surveynumber&searchData=zNPu7s8elCxREc1fF6iihg==&flagval=district&district=10&mandal=205&divi=&khataNoIdselect=1005&ReqType=Citizen&captcha=jXeeT67/4WVaUry9pCEf5Q=="
```

### Example 6: POST JSON request

```bash
curl -X POST http://localhost:8080/api/public-data/parsed \
  -H "Content-Type: application/json" \
  -d '{
    "villageId": "1019004",
    "flagToSearch": "surveynumber",
    "searchData": "zNPu7s8elCxREc1fF6iihg==",
    "flagval": "district",
    "district": "10",
    "mandal": "205",
    "divi": "",
    "khataNoIdselect": "1005",
    "ReqType": "Citizen",
    "captcha": "jXeeT67/4WVaUry9pCEf5Q=="
  }'
```

## How the application works

### Khata-number flow

1. The application reads survey numbers from the configured file.
2. `KhataService.fetchAllFromFile()` loops through all survey numbers.
3. Each survey number is processed concurrently using virtual threads.
4. `DharaniClient.fetchOptionsHtml()` builds a GET request to the portal’s `getKhataNoCitizen` endpoint.
5. The response is raw HTML containing `<option>` entries.
6. `KhataService.extractOptionValues()` uses Jsoup to read the `<option value="...">` elements.
7. Empty values and `"0"` are discarded.
8. The result is returned as a `KhataResult` and written to CSV.

### Public-data flow

1. `PublicDataInfoController` receives either a GET request or a JSON POST.
2. `PublicDataService` fills any missing values from `application.yml`.
3. `PublicDataClient.post()` builds a POST request to `https://bhubharati.telangana.gov.in/getPublicDataInfo`.
4. The call includes the browser cookies and headers that the portal expects.
5. The response is received as raw HTML or encoded content.
6. `LandDetailsParser.parse()` extracts `Pattadar Details` and `Land Details` sections.
7. `LandDetailsHtmlRenderer.render()` turns the parsed fields into a readable HTML page.

## How to build

The repository includes the Gradle wrapper and a standard Spring Boot build.

From the project root:

```bash
./gradlew build
```

If the wrapper has not been created yet in your local environment, the project README suggests:

```bash
gradle wrapper --gradle-version 9.3.0
```

Then build with:

```bash
./gradlew build
```

## How to run

Edit the configuration values in `src/main/resources/application.yml` before starting the app.

Then run:

```bash
./gradlew bootRun
```

The application starts on port 8080 by default.

## Testing

The project includes `spring-boot-starter-test` in `build.gradle`, but no test classes were found in the checked source tree. The build is set to use JUnit Platform:

```gradle
tasks.named('test') {
    useJUnitPlatform()
}
```

At the source level, the project currently focuses on application logic and HTTP integration rather than a broad automated test suite.

## Security and configuration notes

This project is tightly coupled to a public government portal and depends on browser session data. The source code and configuration comments make several important points:

- Cookies are required and are copied from a live browser session.
- `JSESSIONID` is a critical part of the portal session.
- The app sends a `Referer` header and a browser-like `User-Agent`.
- Stale session cookies can produce empty results or failed lookups.
- The `public-data` request block includes sensitive values such as `searchData`, `captcha`, and session-related fields.

Important practical notes:

- Do not share raw `application.yml` values in public repositories if they include active session cookies.
- This application is not a secure or production-grade authentication system.
- It does not implement user login or authorization.
- It uses outbound HTTP calls to a third-party site and depends on that site’s current behavior.
- The app is best understood as a scraping/integration client, not a general API server.

# ROR 1-B PDF  (pure Java - no browser, no extra dependency, no licence issue)

Open:  http://localhost:8080/api/ror/pdf

## Setup (2 minutes)
1. If you added the Playwright line to build.gradle earlier -> DELETE that line. No build.gradle change is needed now.
2. Delete the old file  src/main/java/com/example/dharani/ror/RorPdfService.java  (it used Playwright).
3. Copy the `src` folder of this package into the project root (merge; overwrite RorController.java / RorRecord.java).
4. Gradle > Refresh (Eclipse) and run DharaniClientApplication.

## What is new
src/main/java/com/example/dharani/ror/
  RorController        GET /api/ror/pdf   (?fasli= &khataNumber= &surveyNo= &download=true)
  RorService           fasli / year / date / header values
  RorDataRepository    reads ror-data.json, cached, auto-reloads when the file is edited
  RorRecord, FasliCalendar
  pdf/Ror1BPdfRenderer page layout (title, date, info block, table, repeated header on every page)
  pdf/TeluguText       Telugu shaping with the JDK engine + bundled Noto Sans Telugu font
  pdf/PdfWriter        small streaming PDF writer
src/main/resources/fonts/   Noto Sans Telugu Regular + Bold (SIL Open Font License - free for any use, see OFL.txt)
src/main/resources/ror-data.json

## Settings (all optional)
Put these in src/main/resources/application.properties (or the same keys in application.yml):

    ror.data-location=file:/data/ror.json      # use an external JSON instead of the bundled one
    spring.mvc.async.request-timeout=120s      # only needed if one PDF takes longer than 30 seconds

application.yml form:

    ror:
      data-location: file:/data/ror.json
    spring:
      mvc:
        async:
          request-timeout: 120s

Why the timeout line exists: the PDF is streamed through an asynchronous request, and the embedded Tomcat
cuts any async request after 30 seconds by default. Normal files take a few seconds (50,000 rows ~ 6 s), so
you will not need it. Add it only for extremely large files.

## Copy / search
Every line of text carries its real Unicode text (/ActualText) and each embedded font has a ToUnicode map.
Tested with Poppler (pdftotext): copied / extracted Telugu and search matches are exact. Adobe Acrobat,
Chrome and Edge also read /ActualText. Viewers that ignore it (for example Firefox's built-in viewer) fall back
to the ToUnicode map, which is correct for letters but can mis-order some conjunct letters.

## Notes
- The PDF is streamed page by page: memory stays small for very large files.
- Land type names: only "Metta/ Dry" is translated (from your specimen). Add more in LAND_DESCRIPTION
  in Ror1BPdfRenderer; unknown values are printed as they are.

## Summary

This repository is a Spring Boot integration client for Telangana Dharani/Bhubharati services. It collects survey numbers and khata numbers, and it retrieves land/public-data details using browser-like HTTP requests and HTML parsing. The code demonstrates a layered design built around Spring configuration properties, Java records, and HTTP client integration.

If you are running this project locally, the main operational requirement is correct portal configuration values in `application.yml`, especially the cookies and session tokens.
