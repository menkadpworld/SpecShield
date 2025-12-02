package com.dpw.specshield.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping
@RequiredArgsConstructor
public class ReportController {
    @PostMapping("/report/{id}")
    public ResponseEntity<Map<String, String>> getReportById(
            @PathVariable Long id) {
        log.info("Received report request");

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", "data"));
    }
}
