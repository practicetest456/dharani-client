package com.example.dharani.publicdata.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PublicDataRequest(
        String villageId,
        String flagToSearch,
        String searchData,
        String flagval,
        String district,
        String mandal,
        String divi,
        String khataNoIdselect,
        @JsonProperty("ReqType") String reqType,
        String captcha) {
}