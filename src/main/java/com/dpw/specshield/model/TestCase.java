package com.dpw.specshield.model;

import lombok.Data;

@Data
public class TestCase {
    private String testCaseId;
    private String testType; // happypath, negative, schema-validation
    private Endpoint endpoint;
    private TestRequest request;
    private ExpectedResult expected;
}
