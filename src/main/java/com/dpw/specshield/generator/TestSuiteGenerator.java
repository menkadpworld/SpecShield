package com.dpw.specshield.generator;

import com.dpw.specshield.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.*;
import com.dpw.specshield.model.*;
import java.util.*;

@Component
public class TestSuiteGenerator {

    private final PayloadGenerator payloadGenerator;
    private final ObjectMapper objectMapper;

    public TestSuiteGenerator(PayloadGenerator payloadGenerator) {
        this.payloadGenerator = payloadGenerator;
        this.objectMapper = new ObjectMapper();
    }

    public TestSuite buildTestSuite(List<ApiSpec> apiSpecs,
                                    JsonNode components,
                                    String suiteName,
                                    Map<String, String> dynamicHeaders) {

        TestSuite suite = new TestSuite();
        suite.setTestSuiteName(suiteName);

        List<TestCase> cases = new ArrayList<>();
        int counter = 1;

        for (ApiSpec api : apiSpecs) {
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

            // Only dynamic headers from controller input
            req.setHeaders(dynamicHeaders != null ? new HashMap<>(dynamicHeaders) : new HashMap<>());

            req.setQueryParams(new HashMap<>());
            req.setPathParams(new HashMap<>());

            // Generate payload from schema + components
            Object rawPayload = payloadGenerator.generatePositivePayload(api, components);

            // Serialize to JsonNode (ensures body has example values)
            JsonNode bodyNode = objectMapper.valueToTree(rawPayload);
            req.setBody(bodyNode);

            tc.setRequest(req);

            // Expected
            ExpectedResult expected = new ExpectedResult();
            expected.setStatusCode(200);
            expected.setResponseSchema(api.getName() + "_Response");

            TestAssertion assertion = new TestAssertion();
            assertion.setJsonPath("$");
            assertion.setCondition("NOT_NULL");
            expected.setAssertions(List.of(assertion));

            tc.setExpected(expected);
            cases.add(tc);
        }

        suite.setTestCases(cases);
        return suite;
    }
}
