package com.dpw.specshield.model;

import lombok.Data;
import java.util.List;

@Data
public class ExpectedResult {
    private int statusCode;
    private String responseSchema;
    private List<TestAssertion> assertions;
}
