package com.example.dharani.publicdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "public-data")
public record PublicDataProperties(
        String villageId,
        String flagToSearch,
        String searchData,
        String flagval,
        String district,
        String mandal,
        String divi,
        String khataNoIdselect,
        String reqType,
        String captcha) {
}