package com.dpw.specshield.generator;

import com.dpw.specshield.model.TestSuite;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class TestSuiteSerializer {

    private static final ObjectMapper mapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public static String serialize(TestSuite suite) throws Exception {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(suite);
    }
}