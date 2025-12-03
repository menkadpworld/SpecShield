package com.dpw.specshield.generator;

import com.dpw.specshield.model.ApiSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class PayloadGenerator {

    private final ObjectMapper mapper = new ObjectMapper();

    public JsonNode generatePositivePayload(ApiSpec api, JsonNode components) {
        return generateFromSchema(api.getSchema(), components);
    }

    private JsonNode generateFromSchema(JsonNode schemaNode, JsonNode components) {
        ObjectNode payload = mapper.createObjectNode();
        if (schemaNode == null || schemaNode.isMissingNode()) {
            payload.put("sampleField", "sampleValue");
            return payload;
        }

        // Resolve $ref
        if (schemaNode.has("$ref")) {
            String ref = schemaNode.get("$ref").asText();
            String schemaName = ref.substring(ref.lastIndexOf("/") + 1);
            JsonNode resolved = components.get(schemaName);
            if (resolved != null) schemaNode = resolved;
        }

        JsonNode props = schemaNode.get("properties");
        if (props == null || props.size() == 0) {
            payload.put("sampleField", "sampleValue");
            return payload;
        }

        props.fieldNames().forEachRemaining(fieldName -> {
            JsonNode fieldSchema = props.get(fieldName);
            String type = fieldSchema.has("type") ? fieldSchema.get("type").asText() : "string";

            switch (type.toLowerCase()) {
                case "string": payload.put(fieldName, fieldName + "_sample"); break;
                case "integer":
                case "int": payload.put(fieldName, 100); break;
                case "boolean": payload.put(fieldName, true); break;
                case "number":
                case "float":
                case "double": payload.put(fieldName, 123.45); break;
                default: payload.put(fieldName, "sample"); break;
            }
        });

        return payload;
    }
}
