package com.example.querytranslator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class QueryTranslatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(QueryTranslatorApplication.class, args);
    }
}
