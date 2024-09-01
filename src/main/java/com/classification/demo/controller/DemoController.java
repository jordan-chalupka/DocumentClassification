package com.classification.demo.controller;

import com.classification.demo.service.OpenAIService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api")
public class DemoController {

    private final OpenAIService openAIService;

    public DemoController(OpenAIService openAIService) {
        this.openAIService = openAIService;
    }

    @GetMapping("/classify")
    public String completion(@RequestParam("file") MultipartFile file) throws IOException {
        return openAIService.getOpenAIResponse(file);
    }
}
