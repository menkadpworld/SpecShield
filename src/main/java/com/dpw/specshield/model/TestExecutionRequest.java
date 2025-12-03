package com.dpw.specshield.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "test_execution_requests")
public class TestExecutionRequest {
    @Id
    private String id;
    private String testSuiteName;
    private String originalId; // The ID from the original request
    private String baseUrl;
    private TestSuite testSuite;
    private LocalDateTime createdAt;
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED
}