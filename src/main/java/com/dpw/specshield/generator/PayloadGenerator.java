package com.dpw.specshield.generator;

import com.dpw.specshield.model.ApiSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

        // Resolve $ref for the root schema
        schemaNode = resolveRef(schemaNode, components);

        JsonNode props = schemaNode.get("properties");
        if (props == null || props.size() == 0) {
            payload.put("sampleField", "sampleValue");
            return payload;
        }

        props.fieldNames().forEachRemaining(fieldName -> {
            JsonNode fieldSchema = props.get(fieldName);
            // Resolve $ref for this field
            fieldSchema = resolveRef(fieldSchema, components);

            String type = fieldSchema.has("type") ? fieldSchema.get("type").asText() : "object";

            // Pagination fallback
            if (fieldName.equalsIgnoreCase("pagination") && "object".equalsIgnoreCase(type)) {
                ObjectNode pageNode = mapper.createObjectNode();
                pageNode.put("pageNo", 1);
                pageNode.put("pageSize", 10);
                payload.set(fieldName, pageNode);
                return;
            }

            // Skip filter entirely (generate empty object)
            if (fieldName.equalsIgnoreCase("filter") && "object".equalsIgnoreCase(type)) {
                payload.set(fieldName, mapper.createObjectNode());
                return;
            }

            if (fieldName.equalsIgnoreCase("search") && "object".equalsIgnoreCase(type)) {
                payload.set(fieldName, mapper.createObjectNode());
                return;
            }

            // Generate value based on type or example
            JsonNode example = fieldSchema.get("example");
            if (example != null && !example.isNull()) {
                payload.set(fieldName, example);
                return;
            }

            switch (type.toLowerCase()) {
                case "string":
                    payload.put(fieldName, fieldName + "_sample");
                    break;
                case "integer":
                case "int":
                    payload.put(fieldName, 100);
                    break;
                case "boolean":
                    payload.put(fieldName, true);
                    break;
                case "number":
                case "float":
                case "double":
                    payload.put(fieldName, 123.45);
                    break;
                case "array":
                    ArrayNode arr = mapper.createArrayNode();
                    JsonNode items = fieldSchema.get("items");
                    if (items != null) arr.add(generateFromSchema(items, components));
                    payload.set(fieldName, arr);
                    break;
                case "object":
                default:
                    payload.set(fieldName, generateFromSchema(fieldSchema, components));
                    break;
            }
        });

        return payload;
    }

    private JsonNode resolveRef(JsonNode node, JsonNode components) {
        if (node.has("$ref")) {
            String ref = node.get("$ref").asText();
            String schemaName = ref.substring(ref.lastIndexOf("/") + 1);
            JsonNode resolved = components.get(schemaName);
            if (resolved != null) return resolved;
        }
        return node;
    }
}
