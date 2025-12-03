package com.dpw.specshield.controller;

import com.dpw.specshield.generator.TestSuiteGenerator;
import com.dpw.specshield.generator.TestSuiteSerializer;
import com.dpw.specshield.model.TestSuite;
import com.dpw.specshield.parser.SwaggerParser;
import com.dpw.specshield.services.TestSuiteService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping
@RequiredArgsConstructor
public class ReportController {

    @Autowired
    private TestSuiteService testSuiteService;

    @PostMapping("/report/{id}")
    public ResponseEntity<?> getReportById(
            @PathVariable Long id) throws Exception {
        log.info("Received report request");
        TestSuite suite = testSuiteService.generate();
        String json = TestSuiteSerializer.serialize(suite);
        return ResponseEntity.status(HttpStatus.CREATED).body(json);
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateTestSuite(@RequestBody(required = false) Map<String, String> headers) throws Exception {
        TestSuite suite =  testSuiteService.generate(headers);
        String json = TestSuiteSerializer.serialize(suite);
        return ResponseEntity.status(HttpStatus.CREATED).body(json);
    }
}
