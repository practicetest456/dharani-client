package com.example.dharani.masterdata.claud.survey;

import java.util.List;

/** The khata numbers of one survey number of a village. */
public record KhataList(String villId, String surveyNo, List<String> khataNos) {
}
