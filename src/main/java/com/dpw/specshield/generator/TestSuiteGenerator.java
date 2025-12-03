package com.dpw.specshield.generator;

import com.dpw.specshield.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

            addInvalidIdForGetTestCase(api, counter, inputHeaders, cases);
            addInvalidPaginationTestCase(api, counter, inputHeaders, cases, components);
        }

        suite.setBaseUrl(inputHeaders.get("baseUrl"));
        suite.setTestCases(cases);
        return suite;
    }

    private void addInvalidIdForGetTestCase(ApiSpec api,int counter, Map<String, String> inputHeaders, List<TestCase> cases)
    {
        if ("GET".equalsIgnoreCase(api.getMethod())) {

            Map<String, String> queryParams = api.getQueryParams();
            if (queryParams != null && queryParams.containsKey("id")) {
                // 1. INVALID ID TEST (404 EXPECTED)
                TestCase invalidIdTC = new TestCase();
                invalidIdTC.setTestCaseId(String.format("TC%03d", counter++));
                invalidIdTC.setTestType("negative");

                Endpoint e1 = new Endpoint();
                e1.setMethod(api.getMethod());
                e1.setUrl(api.getPath());
                invalidIdTC.setEndpoint(e1);

                TestRequest req1 = new TestRequest();
                req1.setHeaders(inputHeaders);
                req1.setPathParams(new HashMap<>());
                req1.setQueryParams(Map.of("id", String.valueOf(Integer.MAX_VALUE))); // always invalid
                req1.setBody(null);
                invalidIdTC.setRequest(req1);

                ExpectedResult ex1 = new ExpectedResult();
                ex1.setStatusCode(404);
                TestAssertion statusAssertion1 = new TestAssertion();
                statusAssertion1.setType("statusCode");
                statusAssertion1.setCondition("EQUALS");
                statusAssertion1.setMin(404);
                statusAssertion1.setMax(404);
                invalidIdTC.setExpected(ex1);
                ex1.setAssertions(List.of(statusAssertion1));
                cases.add(invalidIdTC);
            }
        }
    }


    private void addInvalidPaginationTestCase(ApiSpec api,int counter, Map<String, String> inputHeaders, List<TestCase> cases, JsonNode components)
    {
        // INVALID PAGINATION TEST CASE FOR POST APIs
        if ("POST".equalsIgnoreCase(api.getMethod())) {

            // Generate normal positive payload first
            JsonNode payloadNode = payloadGenerator.generatePositivePayload(api, components);
            ObjectNode invalidBody = payloadNode.deepCopy();

            // Check if 'pagination' exists and is an object
            JsonNode paginationRaw = invalidBody.get("pagination");
            if (paginationRaw != null && paginationRaw.isObject()) {
                ObjectNode paginationNode = (ObjectNode) paginationRaw;

                // Inject invalid pagination values
                paginationNode.put("pageNo", -1);
                paginationNode.put("pageSize", 0);

                invalidBody.set("pagination", paginationNode); // optional, already there
            }

            // Build test case
            TestCase invalidPgTest = new TestCase();
            invalidPgTest.setTestCaseId(String.format("TC%03d", counter++));
            invalidPgTest.setTestType("negative");

            // Endpoint
            Endpoint ep = new Endpoint();
            ep.setMethod(api.getMethod());
            ep.setUrl(api.getPath());
            invalidPgTest.setEndpoint(ep);

            // Request
            TestRequest badReq = new TestRequest();
            badReq.setHeaders(inputHeaders != null ? inputHeaders : new HashMap<>());
            badReq.setPathParams(new HashMap<>());
            badReq.setQueryParams(new HashMap<>()); // keep empty
            badReq.setBody(invalidBody);
            invalidPgTest.setRequest(badReq);

            // Expected result
            ExpectedResult expected = new ExpectedResult();
            expected.setStatusCode(400); // invalid pagination should return 400

            TestAssertion statusAssertion = new TestAssertion();
            statusAssertion.setType("statusCode");
            statusAssertion.setCondition("EQUALS");
            statusAssertion.setExpectedValue("400");

            expected.setAssertions(List.of(statusAssertion));
            invalidPgTest.setExpected(expected);

            // Add to test suite
            cases.add(invalidPgTest);
        }

    }
}