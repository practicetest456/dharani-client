package com.example.dharani.config;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dharani")
public record DharaniProperties(
        String baseUrl,
        String villId,
        String flag,
        String referer,
        String surveyFile,
        int concurrency,
        String outputFile,
        Map<String, String> cookies,
        String userAgent,
        Duration connectTimeout,
        Duration readTimeout) {
}