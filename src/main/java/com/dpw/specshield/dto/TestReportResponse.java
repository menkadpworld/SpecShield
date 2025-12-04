package com.dpw.specshield.dto;

import lombok.Data;
import java.util.List;

@Data
public class TestReportResponse {
    private String reportTimestamp;
    private Overview overview;
    private List<TestExecutionSummary> executionDetails;

    @Data
    public static class Overview {
        private String executionTime;
        private Integer total;
        private Integer errors;
        private Integer warnings;
        private Integer successful;
        private Integer pending;
    }

}