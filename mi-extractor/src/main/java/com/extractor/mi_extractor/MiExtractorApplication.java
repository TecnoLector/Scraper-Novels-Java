package com.extractor.mi_extractor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// @SpringBootApplication ya escanea este paquete y todos sus subpaquetes
// (controller/service/processor), así que no hace falta un @ComponentScan explícito.
@SpringBootApplication
public class MiExtractorApplication {
    public static void main(String[] args) {
        SpringApplication.run(MiExtractorApplication.class, args);
    }
}