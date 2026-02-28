package com.extractor.mi_extractor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.extractor.mi_extractor", "com.extractor.mi_extractor.controller", "com.extractor.mi_extractor.service","com.extractor.mi_extractor.processor"})
public class MiExtractorApplication {
    public static void main(String[] args) {
        SpringApplication.run(MiExtractorApplication.class, args);
    }
}