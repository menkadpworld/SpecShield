package com.dpw.specshield.model;

import lombok.Data;
import java.util.List;

@Data
public class Expected {
    private Integer statusCode;
    private String responseSchema;
    private List<Assertion> assertions;
}