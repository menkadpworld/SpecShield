package com.dpw.specshield.model;

import lombok.Data;
import java.util.List;

@Data
public class TestSuite {
    private String testSuiteName;
    private String id;
    private String baseUrl;
    private List<TestCase> testCases;
}