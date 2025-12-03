package com.dpw.specshield.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "test_results")
public class TestResult {
    @Id
    private String id;
    private String testSuiteName;
    private LocalDateTime executionStartTime;
    private LocalDateTime executionEndTime;
    private String executionDuration;
    private Integer totalTests;
    private Integer successfulTests;
    private Integer errorTests;
    private Integer warningTests;
    private Integer pendingTests;
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED
    private List<TestExecution> executions;
}