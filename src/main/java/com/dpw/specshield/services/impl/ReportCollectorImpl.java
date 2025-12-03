package com.dpw.specshield.services.impl;

import com.dpw.specshield.dto.TestReportResponse;
import com.dpw.specshield.model.TestResult;
import com.dpw.specshield.repository.TestResultRepository;
import com.dpw.specshield.services.IReportCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportCollectorImpl implements IReportCollector {

    private final TestResultRepository testResultRepository;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss").withZone(java.time.ZoneId.systemDefault());

    @Override
    public TestReportResponse getReportById(String reportId) {
        log.info("Fetching report for ID: {}", reportId);

        TestResult testResult = testResultRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found with ID: " + reportId));

        TestReportResponse response = new TestReportResponse();
        response.setReportTimestamp(testResult.getExecutionEndTime().atZone(java.time.ZoneId.systemDefault()).format(FORMATTER) + " +0530");

        TestReportResponse.Overview overview = new TestReportResponse.Overview();
        overview.setExecutionTime(testResult.getExecutionDuration());
        overview.setTotal(testResult.getTotalTests());
        overview.setErrors(testResult.getErrorTests());
        overview.setWarnings(testResult.getWarningTests());
        overview.setSuccessful(testResult.getSuccessfulTests());

        response.setOverview(overview);
        response.setExecutionDetails(testResult.getExecutions());

        log.info("Report retrieved successfully for ID: {}", reportId);
        return response;
    }
}