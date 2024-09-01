package com.classification.demo.config;

import io.github.stefanbratanov.jvm.openai.OpenAI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAIConfig {

    @Bean
    public OpenAI openAI(@Value("${openai.api.key}") String apiKey) {
        return OpenAI.newBuilder(apiKey).build();
    }
}
