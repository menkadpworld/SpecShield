package com.dpw.specshield.services.impl;

import com.dpw.specshield.model.*;
import com.dpw.specshield.services.IExecutorService;
import com.dpw.specshield.repository.TestResultRepository;
import com.dpw.specshield.repository.TestExecutionRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutorServiceImpl implements IExecutorService {

    private final WebClient webClient;
    private final TestResultRepository testResultRepository;
    private final TestExecutionRequestRepository testExecutionRequestRepository;
    private final ObjectMapper objectMapper;

    @Override
    public CompletableFuture<String> executeTestSuite(TestSuite testSuite) {
        return executeTestSuiteWithRealTimeUpdates(testSuite, null);
    }

    public CompletableFuture<String> executeTestSuiteWithRealTimeUpdates(TestSuite testSuite, TestResult existingResult) {
        return CompletableFuture.supplyAsync(() -> {
            LocalDateTime startTime = LocalDateTime.now();
            log.info("Starting test suite execution: {}", testSuite.getTestSuiteName());

            Map<String, List<TestCase>> groupedByUrl = groupTestCasesByUrl(testSuite.getTestCases());

            // Use existing result if provided (for real-time updates) or create new one
            TestResult testResult;
            if (existingResult != null) {
                testResult = existingResult;
                testResult.setExecutionStartTime(startTime);
                testResult.setStatus("PROCESSING");
                testResultRepository.save(testResult);
            } else {
                // For direct execution (backwards compatibility)
                testResult = new TestResult();
                testResult.setTestSuiteName(testSuite.getTestSuiteName());
                testResult.setExecutionStartTime(startTime);
                testResult.setStatus("PROCESSING");
                testResult.setTotalTests(testSuite.getTestCases().size());
                testResult.setPendingTests(testSuite.getTestCases().size());
                testResult.setSuccessfulTests(0);
                testResult.setErrorTests(0);
                testResult.setWarningTests(0);
                testResult.setExecutions(new ArrayList<>());

                if (testSuite.getId() != null) {
                    testResult.setId(testSuite.getId());
                }
                testResult = testResultRepository.save(testResult);
            }

            TestResult finalTestResult = testResult;
            List<CompletableFuture<List<TestExecution>>> futures = groupedByUrl.entrySet()
                    .stream()
                    .map(entry -> executeTestGroupWithUpdates(entry.getKey(), entry.getValue(), testSuite.getBaseUrl(), finalTestResult))
                    .toList();

            List<TestExecution> allExecutions = futures.stream()
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .collect(Collectors.toList());

            LocalDateTime endTime = LocalDateTime.now();
            Duration duration = Duration.between(startTime, endTime);

            // Final update
            testResult.setExecutionEndTime(endTime);
            testResult.setExecutionDuration(formatDuration(duration));
            testResult.setStatus("COMPLETED");
            testResult.setPendingTests(0);
            testResult.setExecutions(allExecutions);

            testResult = testResultRepository.save(testResult);

            log.info("Test suite execution completed: {} with {} tests",
                    testSuite.getTestSuiteName(), allExecutions.size());

            return testResult.getId();
        });
    }

    private CompletableFuture<List<TestExecution>> executeTestGroupWithUpdates(String urlGroup, List<TestCase> testCases, String baseUrl, TestResult testResult) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Executing {} test cases for URL group: {}", testCases.size(), urlGroup);

            return testCases.stream()
                    .map(testCase -> {
                        TestExecution execution = executeTestCase(testCase, baseUrl);

                        // Update result in real-time after each test execution
                        updateTestResultRealTime(testResult, execution);

                        return execution;
                    })
                    .collect(Collectors.toList());
        });
    }

    private synchronized void updateTestResultRealTime(TestResult testResult, TestExecution execution) {
        try {
            // Fetch latest result from database to ensure consistency
            TestResult latestResult = testResultRepository.findById(testResult.getId()).orElse(testResult);

            // Add the new execution
            if (latestResult.getExecutions() == null) {
                latestResult.setExecutions(new ArrayList<>());
            }
            latestResult.getExecutions().add(execution);

            // Update counts
            if ("success".equals(execution.getResult())) {
                latestResult.setSuccessfulTests(latestResult.getSuccessfulTests() + 1);
            } else if ("error".equals(execution.getResult())) {
                latestResult.setErrorTests(latestResult.getErrorTests() + 1);
            }

            // Update pending count
            int completedTests = latestResult.getSuccessfulTests() + latestResult.getErrorTests() + latestResult.getWarningTests();
            latestResult.setPendingTests(latestResult.getTotalTests() - completedTests);

            // Save updated result
            testResultRepository.save(latestResult);

            log.debug("Updated test result in real-time: {}/{} tests completed",
                     completedTests, latestResult.getTotalTests());

        } catch (Exception e) {
            log.warn("Failed to update test result in real-time: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "${specshield.kafka.topics.test-execution}")
    public void processTestExecutionRequest(String executionId, TestExecutionRequest request) {
        log.info("Received test execution request from Kafka with ID: {}", executionId);

        try {
            // Update request status to PROCESSING
            request.setStatus("PROCESSING");
            testExecutionRequestRepository.save(request);

            // Create initial test result with pending status
            TestResult initialResult = createInitialTestResult(request);
            testResultRepository.save(initialResult);

            // Execute the test suite asynchronously
            executeTestSuiteAsync(request, initialResult);

        } catch (Exception e) {
            log.error("Error processing test execution request {}: {}", executionId, e.getMessage());
            // Update request status to FAILED
            request.setStatus("FAILED");
            testExecutionRequestRepository.save(request);
        }
    }

    private TestResult createInitialTestResult(TestExecutionRequest request) {
        TestResult result = new TestResult();
        result.setId(request.getId());
        result.setTestSuiteName(request.getTestSuiteName());
        result.setExecutionStartTime(LocalDateTime.now());
        result.setStatus("PENDING");
        result.setTotalTests(request.getTestSuite().getTestCases().size());
        result.setPendingTests(request.getTestSuite().getTestCases().size());
        result.setSuccessfulTests(0);
        result.setErrorTests(0);
        result.setWarningTests(0);
        result.setExecutions(new ArrayList<>());
        return result;
    }

    private void executeTestSuiteAsync(TestExecutionRequest request, TestResult testResult) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Starting asynchronous test suite execution: {}", request.getTestSuiteName());

                // Execute the test suite with real-time updates
                String resultId = executeTestSuiteWithRealTimeUpdates(request.getTestSuite(), testResult).join();

                // Update request status to COMPLETED
                request.setStatus("COMPLETED");
                testExecutionRequestRepository.save(request);

                log.info("Completed asynchronous test suite execution: {} with result ID: {}",
                        request.getTestSuiteName(), resultId);

            } catch (Exception e) {
                log.error("Error in asynchronous test execution for request {}: {}",
                         request.getId(), e.getMessage());

                // Update statuses to FAILED
                testResult.setStatus("FAILED");
                testResultRepository.save(testResult);
                request.setStatus("FAILED");
                testExecutionRequestRepository.save(request);
            }
        });
    }

    private Map<String, List<TestCase>> groupTestCasesByUrl(List<TestCase> testCases) {
        return testCases.stream()
                .collect(Collectors.groupingBy(tc -> extractBaseUrl(tc.getEndpoint().getUrl())));
    }

    private String extractBaseUrl(String url) {
        if (url.startsWith("http")) {
            return url.split("/")[2];
        }
        return "localhost";
    }


    private TestExecution executeTestCase(TestCase testCase, String baseUrl) {
        LocalDateTime executionTime = LocalDateTime.now();
        TestExecution execution = new TestExecution();

        execution.setId(testCase.getTestCaseId());
        execution.setTimestamp(executionTime);
        execution.setScenario(buildScenario(testCase));
        execution.setExpectedResult(buildExpectedResult(testCase.getExpected()));
        execution.setContractPath(testCase.getEndpoint().getUrl());
        execution.setHttpMethod(testCase.getEndpoint().getMethod().toLowerCase());

        try {
            ResponseEntity<String> response = makeHttpRequest(testCase, baseUrl);
            String fullUrl = buildFullUrl(testCase, baseUrl);
            execution.setFullRequestPath(fullUrl);

            boolean testPassed = validateResponse(response, testCase.getExpected());

            if (testPassed) {
                execution.setResult("success");
                execution.setResultDetails(String.format("Response matched expected: actual [%d]",
                        response.getStatusCode().value()));
            } else {
                execution.setResult("error");
                execution.setResultDetails(String.format("Unexpected behaviour: expected [%d], actual [%d]",
                        testCase.getExpected().getStatusCode(), response.getStatusCode().value()));
            }

            execution.setRequestDetails(buildRequestDetails(testCase, fullUrl));

        } catch (WebClientResponseException e) {
            execution.setResult("error");
            execution.setResultDetails(String.format("HTTP Error: %s", e.getMessage()));
            execution.setFullRequestPath(buildFullUrl(testCase, baseUrl));
            execution.setRequestDetails(buildRequestDetails(testCase, buildFullUrl(testCase, baseUrl)));
        } catch (Exception e) {
            log.error("Error executing test case {}: {}", testCase.getTestCaseId(), e.getMessage());
            execution.setResult("error");
            execution.setResultDetails(String.format("Execution Error: %s", e.getMessage()));
        }

        return execution;
    }

    private ResponseEntity<String> makeHttpRequest(TestCase testCase, String baseUrl) {
        String url = buildFullUrl(testCase, baseUrl);
        HttpMethod method = HttpMethod.valueOf(testCase.getEndpoint().getMethod().toUpperCase());

        WebClient.RequestHeadersSpec<?> request;

        if (method == GET) {
            request = webClient.get().uri(url);
        } else if (method == POST) {
            request = webClient.post()
                    .uri(url)
                    .bodyValue(testCase.getRequest().getBody() != null ? testCase.getRequest().getBody() : "");
        } else if (method == PUT) {
            request = webClient.put()
                    .uri(url)
                    .bodyValue(testCase.getRequest().getBody() != null ? testCase.getRequest().getBody() : "");
        } else if (method == DELETE) {
            request = webClient.delete().uri(url);
        } else {
            throw new UnsupportedOperationException("HTTP method not supported: " + method);
        }

        if (testCase.getRequest().getHeaders() != null) {
            testCase.getRequest().getHeaders().forEach(request::header);
        }

        return request.retrieve().toEntity(String.class).block();
    }

    private String buildFullUrl(TestCase testCase, String baseUrl) {
        String url = testCase.getEndpoint().getUrl();

        if (testCase.getRequest().getPathParams() != null) {
            for (Map.Entry<String, String> param : testCase.getRequest().getPathParams().entrySet()) {
                url = url.replace("{" + param.getKey() + "}", param.getValue());
            }
        }

        if (testCase.getRequest().getQueryParams() != null && !testCase.getRequest().getQueryParams().isEmpty()) {
            StringBuilder urlBuilder = new StringBuilder(url);
            urlBuilder.append("?");
            testCase.getRequest().getQueryParams().entrySet().forEach(entry ->
                urlBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append("&"));
            url = urlBuilder.toString().replaceAll("&$", "");
        }

        // Use provided baseUrl instead of hardcoded localhost
        String fullUrl = baseUrl + url;
        return fullUrl;
    }

    private boolean validateResponse(ResponseEntity<String> response, Expected expected) {
        if (!Integer.valueOf(response.getStatusCode().value()).equals(expected.getStatusCode())) {
            return false;
        }

        if (expected.getAssertions() != null && response.getBody() != null) {
            for (Assertion assertion : expected.getAssertions()) {
                if (!validateAssertion(response.getBody(), assertion)) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean validateAssertion(String responseBody, Assertion assertion) {
        try {
            Object value = JsonPath.read(responseBody, assertion.getJsonPath());

            switch (assertion.getCondition()) {
                case "NOT_EMPTY":
                    return value != null && !value.toString().isEmpty();
                case "NOT_NULL":
                    return value != null;
                case "EQUALS":
                    return Objects.equals(value, assertion.getExpectedValue());
                default:
                    return true;
            }
        } catch (Exception e) {
            log.warn("Failed to validate assertion {}: {}", assertion.getJsonPath(), e.getMessage());
            return false;
        }
    }

    private String buildScenario(TestCase testCase) {
        return String.format("Execute %s request to %s",
                testCase.getEndpoint().getMethod(), testCase.getEndpoint().getUrl());
    }

    private String buildExpectedResult(Expected expected) {
        return String.format("Should return [%d]", expected.getStatusCode());
    }

    private TestExecution.RequestDetails buildRequestDetails(TestCase testCase, String fullUrl) {
        TestExecution.RequestDetails details = new TestExecution.RequestDetails();
        details.setHeaders(testCase.getRequest().getHeaders());
        details.setPayload(testCase.getRequest().getBody());
        details.setCurl(buildCurlCommand(testCase, fullUrl));
        return details;
    }

    private String buildCurlCommand(TestCase testCase, String fullUrl) {
        StringBuilder curl = new StringBuilder();
        curl.append("curl -X ").append(testCase.getEndpoint().getMethod().toUpperCase())
            .append(" '").append(fullUrl).append("'");

        if (testCase.getRequest().getHeaders() != null) {
            testCase.getRequest().getHeaders().forEach((key, value) ->
                curl.append(" -H '").append(key).append(": ").append(value).append("'"));
        }

        if (testCase.getRequest().getBody() != null) {
            try {
                String bodyJson = objectMapper.writeValueAsString(testCase.getRequest().getBody());
                curl.append(" -d '").append(bodyJson).append("'");
            } catch (Exception e) {
                curl.append(" -d '").append(testCase.getRequest().getBody().toString()).append("'");
            }
        }

        return curl.toString();
    }

    private TestResult buildTestResult(TestSuite testSuite, LocalDateTime startTime,
                                     LocalDateTime endTime, Duration duration,
                                     List<TestExecution> executions) {
        TestResult result = new TestResult();
        result.setTestSuiteName(testSuite.getTestSuiteName());
        result.setExecutionStartTime(startTime);
        result.setExecutionEndTime(endTime);
        result.setExecutionDuration(formatDuration(duration));
        result.setTotalTests(executions.size());
        result.setSuccessfulTests((int) executions.stream().filter(e -> "success".equals(e.getResult())).count());
        result.setErrorTests((int) executions.stream().filter(e -> "error".equals(e.getResult())).count());
        result.setWarningTests(0);
        result.setPendingTests(0);
        result.setStatus("COMPLETED");
        result.setExecutions(executions);

        return result;
    }

    private String formatDuration(Duration duration) {
        return duration.toMillis() + "ms";
    }
}