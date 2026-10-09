package com.example.dharani.service;

import java.util.List;

public record KhataResult(String surveyNo, List<String> khataNos, String error) {
}