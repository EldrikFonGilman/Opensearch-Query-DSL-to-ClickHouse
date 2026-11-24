package com.example.querytranslator.config;

import org.springframework.boot.jackson.autoconfigure.JsonFactoryBuilderCustomizer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;

@Configuration(proxyBeanMethods = false)
public class JsonConfiguration {
    @Bean
    JsonFactoryBuilderCustomizer strictJsonDuplicateDetection() {
        return builder -> builder.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION);
    }

    @Bean
    JsonMapperBuilderCustomizer rejectTrailingJsonTokens() {
        return builder -> builder.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
}
