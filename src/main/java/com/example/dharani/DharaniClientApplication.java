package com.example.dharani;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DharaniClientApplication {
    public static void main(String[] args) {
        SpringApplication.run(DharaniClientApplication.class, args);
    }
}
