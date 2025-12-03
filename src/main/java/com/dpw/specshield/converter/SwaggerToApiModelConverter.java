package com.dpw.specshield.converter;

import com.dpw.specshield.model.ApiSpec;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SwaggerToApiModelConverter {

    public List<ApiSpec> convert(Map<String, JsonNode> rawSchemas) {
        List<ApiSpec> apiSpecs = new ArrayList<>();

        for (Map.Entry<String, JsonNode> entry : rawSchemas.entrySet()) {
            String apiName = entry.getKey();
            JsonNode schemaNode = entry.getValue();

            String[] parts = apiName.split(" ", 2);
            if (parts.length != 2) continue;

            ApiSpec spec = new ApiSpec();
            spec.setMethod(parts[0]);
            spec.setPath(parts[1]);
            spec.setName(apiName);
            spec.setSchema(schemaNode);

            apiSpecs.add(spec);
        }

        return apiSpecs;
    }
}
