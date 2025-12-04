package com.dpw.specshield.services.impl;

import com.dpw.specshield.model.ExpectedResult;
import com.dpw.specshield.model.TestAssertion;
import com.dpw.specshield.model.TestCase;
import com.dpw.specshield.model.TestExecution;
import com.dpw.specshield.model.TestExecutionRequest;
import com.dpw.specshield.model.TestResult;
import com.dpw.specshield.model.TestSuite;
import com.dpw.specshield.services.IExecutorService;
import com.dpw.specshield.repository.TestResultRepository;
import com.dpw.specshield.repository.TestExecutionRequestRepository;
import com.dpw.specshield.utils.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.HEAD;
import static org.springframework.http.HttpMethod.OPTIONS;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutorServiceImpl implements IExecutorService {

    private final RestTemplate restTemplate;
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

            TestResult testResult;
            if (existingResult != null) {
                testResult = existingResult;
                testResult.setExecutionStartTime(startTime);
                testResult.setStatus("PROCESSING");
                testResultRepository.save(testResult);
            } else {
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

            synchronized (updateLock) {
                TestResult latestResult = testResultRepository.findById(testResult.getId()).orElse(testResult);
                latestResult.setExecutionEndTime(endTime);
                latestResult.setExecutionDuration(formatDuration(duration));
                latestResult.setStatus("COMPLETED");
                latestResult.setPendingTests(0);

                if (latestResult.getExecutions() == null) {
                    latestResult.setExecutions(new ArrayList<>());
                }

                for (TestExecution execution : allExecutions) {
                    boolean exists = latestResult.getExecutions().stream()
                        .anyMatch(e -> execution.getId().equals(e.getId()));
                    if (!exists) {
                        latestResult.getExecutions().add(execution);
                    }
                }

                testResult = testResultRepository.save(latestResult);
            }

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

                        updateTestResultRealTime(testResult, execution);

                        return execution;
                    })
                    .collect(Collectors.toList());
        });
    }

    private final Object updateLock = new Object();

    private void updateTestResultRealTime(TestResult testResult, TestExecution execution) {
        synchronized (updateLock) {
            int maxRetries = 3;
            try {
                for (int retry = 0; retry < maxRetries; retry++) {
                    try {
                        TestResult latestResult = testResultRepository.findById(testResult.getId()).orElse(testResult);

                        if (latestResult.getExecutions() == null) {
                            latestResult.setExecutions(new ArrayList<>());
                        }

                        boolean executionExists = latestResult.getExecutions().stream()
                            .anyMatch(e -> execution.getId().equals(e.getId()));

                        if (!executionExists) {
                            latestResult.getExecutions().add(execution);

                            if ("success".equals(execution.getResult())) {
                                latestResult.setSuccessfulTests(latestResult.getSuccessfulTests() + 1);
                            } else if ("error".equals(execution.getResult())) {
                                latestResult.setErrorTests(latestResult.getErrorTests() + 1);
                            } else if ("warning".equals(execution.getResult())) {
                                latestResult.setWarningTests(latestResult.getWarningTests() + 1);
                            }

                            int completedTests = latestResult.getSuccessfulTests() + latestResult.getErrorTests() + latestResult.getWarningTests();
                            latestResult.setPendingTests(latestResult.getTotalTests() - completedTests);

                            testResultRepository.save(latestResult);

                            log.debug("Updated test result in real-time: {}/{} tests completed",
                                     completedTests, latestResult.getTotalTests());
                        } else {
                            log.debug("Execution {} already exists, skipping duplicate update", execution.getId());
                        }
                        break;
                    } catch (Exception e) {
                        if (retry == maxRetries - 1) {
                            throw e;
                        }
                        log.warn("Retry {} failed for test result update: {}", retry + 1, e.getMessage());
                        Thread.sleep(100);
                    }
                }

            } catch (Exception e) {
                log.error("Failed to update test result in real-time after {} retries: {}", maxRetries, e.getMessage());
            }
        }
    }

    @KafkaListener(topics = "${specshield.kafka.topics.test-execution}")
    public void processTestExecutionRequest(@Header(KafkaHeaders.RECEIVED_KEY) String executionId,
                                          @Payload TestExecutionRequest request,
                                          Acknowledgment acknowledgment) {
        log.info("Received test execution request from Kafka with ID: {}", executionId);

        try {
            request.setStatus("PROCESSING");
            testExecutionRequestRepository.save(request);

            TestResult initialResult = testResultRepository.findById(executionId).get();

            executeTestSuiteAsync(request, initialResult);

            acknowledgment.acknowledge();
            log.debug("Message acknowledged for execution ID: {}", executionId);

        } catch (Exception e) {
            log.error("Error processing test execution request {}: {}", executionId, e.getMessage());
            try {
                request.setStatus("FAILED");
                testExecutionRequestRepository.save(request);
                acknowledgment.acknowledge();
                log.debug("Message acknowledged after failure for execution ID: {}", executionId);
            } catch (Exception saveException) {
                log.error("Failed to save error status for execution {}: {}", executionId, saveException.getMessage());
                throw saveException;
            }
        }
    }

    private void executeTestSuiteAsync(TestExecutionRequest request, TestResult testResult) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Starting asynchronous test suite execution: {}", request.getTestSuiteName());

                String resultId = executeTestSuiteWithRealTimeUpdates(request.getTestSuite(), testResult).join();

                request.setStatus("COMPLETED");
                testExecutionRequestRepository.save(request);

                log.info("Completed asynchronous test suite execution: {} with result ID: {}",
                        request.getTestSuiteName(), resultId);

            } catch (Exception e) {
                log.error("Error in asynchronous test execution for request {}: {}",
                         request.getId(), e.getMessage());

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
        execution.setExpectedResult(testCase.getExpected());
        execution.setContractPath(testCase.getEndpoint().getUrl());
        execution.setHttpMethod(testCase.getEndpoint().getMethod().toLowerCase());

        try {
            ResponseEntity<String> response = makeHttpRequest(testCase, baseUrl);
            String fullUrl = buildFullUrl(testCase, baseUrl);
            execution.setFullRequestPath(fullUrl);

            execution.setResponseDetails(buildResponseDetails(response));

            boolean testPassed = validateResponse(response, execution.getExpectedResult());

            if (testPassed) {
                execution.setResult("success");
                execution.setResultDetails(String.format("Response matched expected: actual [%d]",
                        response.getStatusCode().value()));
            } else {
                execution.setResult("error");
                execution.setResultDetails(String.format("Unexpected behaviour: expected [%d], actual [%d]",
                        execution.getExpectedResult().getStatusCode(), response.getStatusCode().value()));
            }

            execution.setRequestDetails(buildRequestDetails(testCase, fullUrl));

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            String fullUrl = buildFullUrl(testCase, baseUrl);
            execution.setFullRequestPath(fullUrl);
            execution.setRequestDetails(buildRequestDetails(testCase, fullUrl));
            execution.setResponseDetails(buildResponseDetailsFromRestException(e));

            // Check if this is an expected error status code
            boolean testPassed = validateResponseFromRestException(e, execution.getExpectedResult());

            if (testPassed) {
                execution.setResult("success");
                execution.setResultDetails(String.format("Response matched expected: actual [%d]", e.getStatusCode().value()));
            } else {
                execution.setResult("error");
                execution.setResultDetails(String.format("Unexpected behaviour: expected [%d], actual [%d]",
                        execution.getExpectedResult().getStatusCode(), e.getStatusCode().value()));
            }
        } catch (Exception e) {
            log.error("Error executing test case {}: {}", testCase.getTestCaseId(), e.getMessage());
            execution.setResult("error");
            execution.setResultDetails(String.format("Execution Error: %s", e.getMessage()));

            // Set empty response details for general exceptions
            TestExecution.ResponseDetails details = new TestExecution.ResponseDetails();
            details.setResponseStatus(null);
            details.setResponseBody("Error occurred before receiving response: " + e.getMessage());
            details.setResponseHeaders(new HashMap<>());
            execution.setResponseDetails(details);
        }

        return execution;
    }

    private ResponseEntity<String> makeHttpRequest(TestCase testCase, String baseUrl) {
        String url = buildFullUrl(testCase, baseUrl);
        HttpMethod method = HttpMethod.valueOf(testCase.getEndpoint().getMethod().toUpperCase());

        // Build headers
        HttpHeaders headers = new HttpHeaders();
        if (testCase.getRequest().getHeaders() != null) {
            testCase.getRequest().getHeaders().forEach(headers::add);
        }

        // Build request body
        String requestBody = null;
        if (testCase.getRequest().getBody() != null) {
            requestBody = JsonUtils.toJsonString(testCase.getRequest().getBody());
        }

        // Create HTTP entity
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // Make the request using RestTemplate
        return restTemplate.exchange(url, method, entity, String.class);
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

        String fullUrl = baseUrl + url;
        return fullUrl;
    }


    private boolean validateResponse(ResponseEntity<String> response, ExpectedResult expectedResult) {
        if (!Integer.valueOf(response.getStatusCode().value()).equals(expectedResult.getStatusCode())) {
            return false;
        }

        if (expectedResult.getAssertions() != null && response.getBody() != null) {
            for (TestAssertion assertion : expectedResult.getAssertions()) {
                if (!validateTestAssertion(response.getBody(), assertion)) {
                    return false;
                }
            }
        }

        return true;
    }


    private boolean validateTestAssertion(String responseBody, TestAssertion assertion) {
        try {
            Object value = JsonPath.read(responseBody, assertion.getJsonPath());

            switch (assertion.getCondition()) {
                case "NOT_EMPTY":
                    return value != null && !value.toString().isEmpty();
                case "NOT_NULL":
                    return value != null;
                case "EQUALS":
                    return Objects.equals(value != null ? value.toString() : null, assertion.getExpectedValue());
                default:
                    return true;
            }
        } catch (Exception e) {
            log.warn("Failed to validate test assertion {}: {}", assertion.getJsonPath(), e.getMessage());
            return false;
        }
    }

    private String buildScenario(TestCase testCase) {
        return String.format("Execute %s request to %s",
                testCase.getEndpoint().getMethod(), testCase.getEndpoint().getUrl());
    }


    private TestExecution.RequestDetails buildRequestDetails(TestCase testCase, String fullUrl) {
        TestExecution.RequestDetails details = new TestExecution.RequestDetails();
        details.setHeaders(testCase.getRequest().getHeaders());

        if (testCase.getRequest().getBody() != null) {
            try {
                details.setPayload(objectMapper.writeValueAsString(testCase.getRequest().getBody()));
            } catch (Exception e) {
                details.setPayload(testCase.getRequest().getBody().toString());
            }
        } else {
            details.setPayload(null);
        }

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

    private String formatDuration(Duration duration) {
        return duration.toMillis() + "ms";
    }

    private TestExecution.ResponseDetails buildResponseDetails(ResponseEntity<String> response) {
        TestExecution.ResponseDetails details = new TestExecution.ResponseDetails();
        details.setResponseStatus(response.getStatusCode().value());
        details.setResponseBody(response.getBody());

        Map<String, String> headerMap = new HashMap<>();
        response.getHeaders().forEach((key, values) -> {
            if (values != null && !values.isEmpty()) {
                headerMap.put(key, String.join(", ", values));
            }
        });
        details.setResponseHeaders(headerMap);

        return details;
    }


    private TestExecution.ResponseDetails buildResponseDetailsFromRestException(HttpClientErrorException e) {
        TestExecution.ResponseDetails details = new TestExecution.ResponseDetails();
        details.setResponseStatus(e.getStatusCode().value());
        details.setResponseBody(e.getResponseBodyAsString());

        Map<String, String> headerMap = new HashMap<>();
        e.getResponseHeaders().forEach((key, values) -> {
            if (values != null && !values.isEmpty()) {
                headerMap.put(key, String.join(", ", values));
            }
        });
        details.setResponseHeaders(headerMap);

        return details;
    }

    private TestExecution.ResponseDetails buildResponseDetailsFromRestException(HttpServerErrorException e) {
        TestExecution.ResponseDetails details = new TestExecution.ResponseDetails();
        details.setResponseStatus(e.getStatusCode().value());
        details.setResponseBody(e.getResponseBodyAsString());

        Map<String, String> headerMap = new HashMap<>();
        e.getResponseHeaders().forEach((key, values) -> {
            if (values != null && !values.isEmpty()) {
                headerMap.put(key, String.join(", ", values));
            }
        });
        details.setResponseHeaders(headerMap);

        return details;
    }

    private TestExecution.ResponseDetails buildResponseDetailsFromRestException(Exception e) {
        if (e instanceof HttpClientErrorException clientException) {
            return buildResponseDetailsFromRestException(clientException);
        } else if (e instanceof HttpServerErrorException serverException) {
            return buildResponseDetailsFromRestException(serverException);
        }

        // Fallback for other exceptions
        TestExecution.ResponseDetails details = new TestExecution.ResponseDetails();
        details.setResponseStatus(null);
        details.setResponseBody("Error occurred: " + e.getMessage());
        details.setResponseHeaders(new HashMap<>());
        return details;
    }

    private boolean validateResponseFromRestException(HttpClientErrorException exception, ExpectedResult expectedResult) {
        if (!Integer.valueOf(exception.getStatusCode().value()).equals(expectedResult.getStatusCode())) {
            return false;
        }

        if (expectedResult.getAssertions() != null && exception.getResponseBodyAsString() != null) {
            for (TestAssertion assertion : expectedResult.getAssertions()) {
                // Skip status code assertions as they are already validated above
                if ("statusCode".equals(assertion.getType())) {
                    continue;
                }
                if (!validateTestAssertion(exception.getResponseBodyAsString(), assertion)) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean validateResponseFromRestException(HttpServerErrorException exception, ExpectedResult expectedResult) {
        if (!Integer.valueOf(exception.getStatusCode().value()).equals(expectedResult.getStatusCode())) {
            return false;
        }

        if (expectedResult.getAssertions() != null && exception.getResponseBodyAsString() != null) {
            for (TestAssertion assertion : expectedResult.getAssertions()) {
                // Skip status code assertions as they are already validated above
                if ("statusCode".equals(assertion.getType())) {
                    continue;
                }
                if (!validateTestAssertion(exception.getResponseBodyAsString(), assertion)) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean validateResponseFromRestException(Exception exception, ExpectedResult expectedResult) {
        if (exception instanceof HttpClientErrorException clientException) {
            return validateResponseFromRestException(clientException, expectedResult);
        } else if (exception instanceof HttpServerErrorException serverException) {
            return validateResponseFromRestException(serverException, expectedResult);
        }
        return false;
    }
}