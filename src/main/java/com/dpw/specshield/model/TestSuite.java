package com.dpw.specshield.model;

import lombok.Data;
import java.util.List;

@Data
public class TestSuite {
    private String testSuiteName;
    private List<TestCase> testCases;
}

