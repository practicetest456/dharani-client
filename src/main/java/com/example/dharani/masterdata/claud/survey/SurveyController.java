package com.example.dharani.masterdata.claud.survey;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.dharani.masterdata.claud.portal.PortalException;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

/**
 * GET /api/bhubharati/survey-khatas?villId=1020008    -> every survey number of the village with its khata numbers
 *     [ {"surveyNo":"1","khataNos":["4"],"error":null}, ... ]   (streamed in the portal's order while it is still
 *     being collected; add refresh=true to skip the cache, includePlaceholder=true to include the "0" entry)
 * GET /api/bhubharati/surveys?villId=1020008          -> just the survey numbers
 * GET /api/bhubharati/khatas?villId=1020008&surveyNo=1 -> the khata numbers of one survey number
 */
@RestController("claudbhubharatiSurveyController")
@RequestMapping("/api/claudbhubharati")
public class SurveyController {

	private static final Logger log = LoggerFactory.getLogger(SurveyController.class);
	private static final Pattern VILLAGE_ID = Pattern.compile("^\\d{4,10}$");
	private static final Pattern SURVEY_NO = Pattern.compile("^[\\p{L}\\p{M}\\p{N}/\\-. ]{1,40}$");
	private static final int FLUSH_EVERY = 100;

	private final SurveyKhataService service;
	private final ObjectMapper mapper;

	public SurveyController(SurveyKhataService service, ObjectMapper mapper) {
		this.service = service;
		this.mapper = mapper;
	}

	@GetMapping("/surveys")
	public SurveyList surveys(@RequestParam(name = "villId") String villId,
			@RequestParam(name = "refresh", defaultValue = "false") boolean refresh) {
		requireVillageId(villId);
		try {
			List<String> surveyNos = service.surveyNos(villId, refresh);
			return new SurveyList(villId, surveyNos.size(), surveyNos);
		} catch (PortalException e) {
			throw badGateway(villId, e);
		}
	}

	@GetMapping("/khatas")
	public KhataList khatas(@RequestParam(name = "villId") String villId,
			@RequestParam(name = "surveyNo") String surveyNo) {
		requireVillageId(villId);
		requireSurveyNo(surveyNo);
		try {
			return new KhataList(villId, surveyNo.trim(), service.khataNos(villId, surveyNo.trim()));
		} catch (PortalException e) {
			throw badGateway(villId, e);
		}
	}

	/**
	 * Written straight to the response (not through Spring's asynchronous streaming), so a first, uncached call for a
	 * big village is not cut off by the 30-second asynchronous time-out. Results are sent in order as they complete.
	 */
	@GetMapping("/survey-khatas")
	public void surveyKhatas(@RequestParam(name = "villId") String villId,
			@RequestParam(name = "refresh", defaultValue = "false") boolean refresh,
			@RequestParam(name = "includePlaceholder", defaultValue = "false") boolean includePlaceholder,
			HttpServletResponse response) throws IOException {
		requireVillageId(villId);

		List<Future<SurveyKhata>> plan;
		try {
			plan = service.plan(villId, refresh, includePlaceholder); // fails here, before any byte is sent
		} catch (PortalException e) {
			throw badGateway(villId, e);
		}

		response.setStatus(HttpStatus.OK.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.setHeader("Cache-Control", "private, max-age=300");
		try (JsonGenerator out = mapper.getFactory().createGenerator(response.getOutputStream(), JsonEncoding.UTF8)) {
			out.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
			out.writeStartArray();
			int written = 0;
			for (Future<SurveyKhata> future : plan) {
				out.writeObject(next(future));
				if (++written % FLUSH_EVERY == 0) {
					out.flush(); // lets the caller start reading while the rest is still being fetched
				}
			}
			out.writeEndArray();
		} catch (IOException e) {
			service.cancel(plan); // the caller went away: stop fetching for nobody
			log.info("Survey/khata response for village {} was interrupted: {}", villId, e.toString());
			throw e;
		}
	}

	private static SurveyKhata next(Future<SurveyKhata> future) throws IOException {
		try {
			return future.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new InterruptedIOException("Interrupted while collecting survey numbers");
		} catch (ExecutionException e) {
			throw new IOException("Could not collect survey numbers", e);
		}
	}

	private static void requireVillageId(String villId) {
		if (villId == null || !VILLAGE_ID.matcher(villId.trim()).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "villId must be a number of 4 to 10 digits");
		}
	}

	private static void requireSurveyNo(String surveyNo) {
		if (surveyNo == null || !SURVEY_NO.matcher(surveyNo.trim()).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "surveyNo contains invalid characters");
		}
	}

	private static ResponseStatusException badGateway(String villId, PortalException e) {
		log.warn("Bhubharati portal problem for village {}: {}", villId, e.getMessage());
		return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
				"The Bhubharati portal did not return the data. Try again shortly.", e);
	}
}
