package com.example.dharani.masterdata.claud.survey;

import java.util.List;

/** All survey numbers of a village. */
public record SurveyList(String villId, int totalSurveys, List<String> surveyNos) {
}
