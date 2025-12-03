package com.dpw.specshield.model;

import lombok.Data;

@Data
public class TestAssertion {
    private String jsonPath;
    private String condition;     // NOT_EMPTY, EQUALS, NOT_NULL
    private String expectedValue;
    private String type;
    private Integer min;
    private Integer max;
}

