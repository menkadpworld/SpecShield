package com.dpw.specshield.model;

import lombok.Data;

@Data
public class Assertion {
    private String jsonPath;
    private String condition;
    private Object expectedValue;
}