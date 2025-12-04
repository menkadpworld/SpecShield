package com.dpw.specshield.services.impl;

import com.dpw.specshield.dto.TestReportResponse;
import com.dpw.specshield.dto.TestExecutionSummary;
import com.dpw.specshield.model.TestResult;
import com.dpw.specshield.model.TestExecution;
import com.dpw.specshield.repository.TestResultRepository;
import com.dpw.specshield.services.IReportCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportCollectorImpl implements IReportCollector {

    private final TestResultRepository testResultRepository;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss").withZone(java.time.ZoneId.systemDefault());

    @Override
    public TestReportResponse getReportById(String reportId, int page, int size) {
        log.info("Fetching report for ID: {} with pagination - page: {}, size: {}", reportId, page, size);

        TestResult testResult = testResultRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found with ID: " + reportId));

        TestReportResponse response = new TestReportResponse();
        if (testResult.getExecutionEndTime() != null) {
            response.setReportTimestamp(testResult.getExecutionEndTime().atZone(java.time.ZoneId.systemDefault()).format(FORMATTER) + " +0530");
        } else {
            response.setReportTimestamp(testResult.getExecutionStartTime().atZone(java.time.ZoneId.systemDefault()).format(FORMATTER) + " +0530");
        }

        TestReportResponse.Overview overview = new TestReportResponse.Overview();
        overview.setExecutionTime(testResult.getExecutionDuration() != null ? testResult.getExecutionDuration() : "In Progress");
        overview.setTotal(testResult.getTotalTests());
        overview.setErrors(testResult.getErrorTests());
        overview.setWarnings(testResult.getWarningTests());
        overview.setSuccessful(testResult.getSuccessfulTests());
        overview.setPending(testResult.getPendingTests());

        response.setOverview(overview);

        List<TestExecutionSummary> paginatedExecutions = getPaginatedExecutionSummaries(testResult.getExecutions(), page, size);
        response.setExecutionDetails(paginatedExecutions);

        log.info("Report retrieved successfully for ID: {} with {} execution details", reportId, paginatedExecutions.size());
        return response;
    }

    private List<TestExecutionSummary> getPaginatedExecutionSummaries(List<TestExecution> executions, int page, int size) {
        if (executions == null || executions.isEmpty()) {
            return List.of();
        }

        int startIndex = page * size;
        int endIndex = Math.min(startIndex + size, executions.size());

        if (startIndex >= executions.size()) {
            return List.of();
        }

        return executions.subList(startIndex, endIndex).stream()
                .map(this::convertToSummary)
                .toList();
    }

    private TestExecutionSummary convertToSummary(TestExecution execution) {
        TestExecutionSummary summary = new TestExecutionSummary();
        summary.setId(execution.getId());
        summary.setTimestamp(execution.getTimestamp());
        summary.setScenario(execution.getScenario());
        summary.setResult(execution.getResult());
        summary.setResultDetails(execution.getResultDetails());
        summary.setContractPath(execution.getContractPath());
        summary.setHttpMethod(execution.getHttpMethod());
        return summary;
    }

    @Override
    public TestExecution getTestCaseDetail(String reportId, String testCaseId) {
        log.info("Fetching test case detail for report ID: {} and test case ID: {}", reportId, testCaseId);

        TestResult testResult = testResultRepository.findById(reportId)
                .orElseThrow(() -> new RuntimeException("Report not found with ID: " + reportId));

        if (testResult.getExecutions() == null || testResult.getExecutions().isEmpty()) {
            throw new RuntimeException("No executions found for report ID: " + reportId);
        }

        TestExecution testExecution = testResult.getExecutions().stream()
                .filter(execution -> testCaseId.equals(execution.getId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Test case not found with ID: " + testCaseId + " in report: " + reportId));

        log.info("Test case detail retrieved successfully for ID: {}", testCaseId);
        return testExecution;
    }

}