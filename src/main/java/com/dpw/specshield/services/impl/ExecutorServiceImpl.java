package com.dpw.specshield.services.impl;

import com.dpw.specshield.model.*;
import com.dpw.specshield.services.IExecutorService;
import com.dpw.specshield.repository.TestResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
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
    private final ObjectMapper objectMapper;

    @Override
    public CompletableFuture<String> executeTestSuite(TestSuite testSuite) {
        return CompletableFuture.supplyAsync(() -> {
            LocalDateTime startTime = LocalDateTime.now();
            log.info("Starting test suite execution: {}", testSuite.getTestSuiteName());

            Map<String, List<TestCase>> groupedByUrl = groupTestCasesByUrl(testSuite.getTestCases());

            List<CompletableFuture<List<TestExecution>>> futures = groupedByUrl.entrySet()
                    .stream()
                    .map(entry -> executeTestGroup(entry.getKey(), entry.getValue(), testSuite.getBaseUrl()))
                    .toList();

            List<TestExecution> allExecutions = futures.stream()
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .collect(Collectors.toList());

            LocalDateTime endTime = LocalDateTime.now();
            Duration duration = Duration.between(startTime, endTime);

            TestResult testResult = buildTestResult(testSuite, startTime, endTime, duration, allExecutions);

            // Use the provided ID from the request
            if (testSuite.getId() != null) {
                testResult.setId(testSuite.getId());
            }

            testResult = testResultRepository.save(testResult);

            log.info("Test suite execution completed: {} with {} tests",
                    testSuite.getTestSuiteName(), allExecutions.size());

            return testResult.getId();
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

    private CompletableFuture<List<TestExecution>> executeTestGroup(String urlGroup, List<TestCase> testCases, String baseUrl) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Executing {} test cases for URL group: {}", testCases.size(), urlGroup);

            return testCases.stream()
                    .map(testCase -> executeTestCase(testCase, baseUrl))
                    .collect(Collectors.toList());
        });
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
        result.setExecutions(executions);

        return result;
    }

    private String formatDuration(Duration duration) {
        return duration.toMillis() + "ms";
    }
}