package com.example.dharani.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.dharani.service.KhataResult;
import com.example.dharani.service.KhataService;

@RestController
@RequestMapping("/api")
public class DharaniController {

	private final KhataService service;

	public DharaniController(KhataService service) {
		this.service = service;
	}

	/** Runs through every line in survey-numbers.txt. */
	@GetMapping("/khata-numbers")
	public List<KhataResult> all() {
		return service.fetchAllFromFile();
	}

	/** Test a single survey number: /api/khata-numbers/single?surveyNo=859/2 */
	@GetMapping("/khata-numbers/single")
	public KhataResult single(@RequestParam("surveyNo") String surveyNo,
			@RequestParam(name = "villageId", required = false) String villageId) {
		return service.fetchOne(surveyNo, villageId);
	}
}