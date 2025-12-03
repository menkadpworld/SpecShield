package com.dpw.specshield.generator;

import com.dpw.specshield.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class TestSuiteGenerator {

    private final PayloadGenerator payloadGenerator = new PayloadGenerator();
    private final ObjectMapper mapper = new ObjectMapper();
    private final TestIdMapping testIdMapping;

    public TestSuiteGenerator(TestIdMapping testIdMapping) {
        this.testIdMapping = testIdMapping;
    }

    public TestSuite buildTestSuite(List<ApiSpec> apiSpecs, JsonNode components,
                                    String suiteName, Map<String, String> inputHeaders) {

        TestSuite suite = new TestSuite();
        suite.setTestSuiteName(suiteName);
        List<TestCase> cases = new ArrayList<>();
        int counter = 1;

        for (ApiSpec api : apiSpecs) {
            // Skip APIs without response schema
            if (api.getResponseSchema() == null) continue;

            TestCase tc = new TestCase();
            tc.setTestCaseId(String.format("TC%03d", counter++));
            tc.setTestType("happypath");

            // Endpoint
            Endpoint endpoint = new Endpoint();
            endpoint.setMethod(api.getMethod());
            endpoint.setUrl(api.getPath());
            tc.setEndpoint(endpoint);

            // Request
            TestRequest req = new TestRequest();
            req.setHeaders(inputHeaders != null ? inputHeaders : new HashMap<>());
            req.setPathParams(new HashMap<>());
            req.setQueryParams(new HashMap<>());

            if ("GET".equalsIgnoreCase(api.getMethod())) {
                //req.setBody(null);

                // populate query parameters
                Map<String, String> queryParams = api.getQueryParams();
                if (queryParams != null) {
                    queryParams.forEach((param, val) -> {
                        if ("id".equalsIgnoreCase(param)) {
                            Integer testId = testIdMapping.getIdForEndpoint(api.getPath());
                            if (testId != null) req.getQueryParams().put(param, String.valueOf(testId));
                        } else {
                            req.getQueryParams().put(param, null); // keep empty
                        }
                    });
                }
            } else {
                // POST/PUT → generate payload
                JsonNode payload = payloadGenerator.generatePositivePayload(api, components);
                req.setBody(payload);
            }

            tc.setRequest(req);

            // Expected
            ExpectedResult expected = new ExpectedResult();
            expected.setStatusCode(200);
            expected.setResponseSchema(api.getResponseSchema().toString());

            TestAssertion statusAssertion = new TestAssertion();
            statusAssertion.setType("statusCode");
            statusAssertion.setCondition("IN_RANGE");
            statusAssertion.setMin(200);
            statusAssertion.setMax(299);

            TestAssertion bodyAssertion = new TestAssertion();
            bodyAssertion.setType("body");
            bodyAssertion.setCondition("NOT_NULL_OR_EMPTY");
            bodyAssertion.setJsonPath("$");

            expected.setAssertions(List.of(statusAssertion, bodyAssertion));

            tc.setExpected(expected);

            cases.add(tc);
        }

        suite.setTestCases(cases);
        return suite;
    }
}