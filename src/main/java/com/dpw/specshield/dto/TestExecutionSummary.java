package com.dpw.specshield.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TestExecutionSummary {
    private String id;
    private LocalDateTime timestamp;
    private String scenario;
    private String result;
    private String resultDetails;
    private String contractPath;
    private String httpMethod;
}