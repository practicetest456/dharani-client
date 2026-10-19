package com.example.dharani.masterdata.claud.survey;

import java.util.List;

/**
 * One survey number of a village with its khata numbers. {@code error} is null when the portal answered; when the
 * portal failed for this survey number it says so (and {@code khataNos} is empty), without spoiling the rest.
 */
public record SurveyKhata(String surveyNo, List<String> khataNos, String error) {
}
