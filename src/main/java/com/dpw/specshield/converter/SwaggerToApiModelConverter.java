package com.dpw.specshield.converter;

import com.dpw.specshield.model.ApiSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SwaggerToApiModelConverter {

    public List<ApiSpec> convert(Map<String, JsonNode> rawSchemas) {
        List<ApiSpec> apiSpecs = new ArrayList<>();

        for (Map.Entry<String, JsonNode> entry : rawSchemas.entrySet()) {
            String apiName = entry.getKey();
            JsonNode wrapper = entry.getValue();

            String[] parts = apiName.split(" ", 2);
            if (parts.length != 2) continue;

            ApiSpec spec = new ApiSpec();
            spec.setMethod(parts[0]);
            spec.setPath(parts[1]);
            spec.setName(apiName);

            // request body schema
            JsonNode bodySchema = wrapper.get("schema");
            spec.setSchema(bodySchema);

            // query params
            Map<String, String> queryParams = new HashMap<>();
            JsonNode qp = wrapper.get("_queryParams");
            if (qp != null && qp.isObject()) {
                qp.fields().forEachRemaining(e -> queryParams.put(e.getKey(), e.getValue().asText()));
            }
            spec.setQueryParams(queryParams);

            // response schema
            JsonNode respSchema = wrapper.get("_responseSchema");
            if (respSchema != null && !respSchema.isMissingNode()) {
                spec.setResponseSchema(respSchema);
            }

            apiSpecs.add(spec);
        }

        return apiSpecs;
    }
}
