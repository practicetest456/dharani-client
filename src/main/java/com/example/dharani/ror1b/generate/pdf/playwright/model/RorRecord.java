package com.example.dharani.ror1b.generate.pdf.playwright.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RorRecord(Map<String, String> pattadarDetails, Map<String, String> landDetails) {
}
