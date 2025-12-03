package com.dpw.specshield.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Component
public class SwaggerParser {

    private final JsonNode swaggerRoot;
    private final ObjectMapper mapper = new ObjectMapper();

    public SwaggerParser(@Value("${swagger.url}") String swaggerJsonPath) throws Exception {
        if (swaggerJsonPath.startsWith("classpath:")) {
            String path = swaggerJsonPath.substring(10);
            InputStream is = getClass().getClassLoader().getResourceAsStream(path);
            if (is == null) throw new RuntimeException("Swagger not found in classpath: " + path);
            swaggerRoot = mapper.readTree(is);
        } else if (swaggerJsonPath.startsWith("http")) {
            swaggerRoot = mapper.readTree(new URL(swaggerJsonPath));
        } else {
            swaggerRoot = mapper.readTree(new File(swaggerJsonPath));
        }
    }

    // Returns body + query param + response schema
    public Map<String, JsonNode> getSchemas() {
        Map<String, JsonNode> schemaMap = new HashMap<>();
        JsonNode pathsNode = swaggerRoot.path("paths");
        if (pathsNode.isMissingNode()) return schemaMap;

        Iterator<Map.Entry<String, JsonNode>> paths = pathsNode.fields();
        while (paths.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = paths.next();
            String path = pathEntry.getKey();
            JsonNode methodsNode = pathEntry.getValue();

            Iterator<Map.Entry<String, JsonNode>> methods = methodsNode.fields();
            while (methods.hasNext()) {
                Map.Entry<String, JsonNode> methodEntry = methods.next();
                String httpMethod = methodEntry.getKey().toUpperCase();
                JsonNode operation = methodEntry.getValue();

                // Skip PATCH/PUT and POST non-list endpoints
                if (skipEndpoint(path, httpMethod)) continue;

                // Wrapper node to hold body + queryParams + responseSchema
                JsonNode wrapper = mapper.createObjectNode();

                // requestBody schema
                JsonNode bodySchema = operation.at("/requestBody/content/application~1json/schema");
                if (!bodySchema.isMissingNode()) {
                    ((ObjectNode) wrapper).set("schema", bodySchema);
                }

                // query parameters
                JsonNode params = operation.get("parameters");
                ObjectNode qpNode = mapper.createObjectNode();
                if (params != null && params.isArray()) {
                    for (JsonNode p : params) {
                        if ("query".equalsIgnoreCase(p.path("in").asText())) {
                            String name = p.path("name").asText();
                            String type = p.path("schema").path("type").asText();
                            qpNode.put(name, type);
                        }
                    }
                }
                ((ObjectNode) wrapper).set("_queryParams", qpNode);

                // response schema (200)
                JsonNode responses = operation.path("responses");
                JsonNode resp200 = responses.path("200").path("content").path("*/*").path("schema");
                if (!resp200.isMissingNode()) {
                    ((ObjectNode) wrapper).set("_responseSchema", resp200);
                }

                schemaMap.put(httpMethod + " " + path, wrapper);
            }
        }
        return schemaMap;
    }

    private boolean skipEndpoint(String path, String httpMethod) {
        String p = path.toLowerCase();
        if ("PATCH".equalsIgnoreCase(httpMethod) || "PUT".equalsIgnoreCase(httpMethod)
                || "OPTIONS".equalsIgnoreCase(httpMethod) ||"HEAD".equalsIgnoreCase(httpMethod)) return true;
        if ("POST".equalsIgnoreCase(httpMethod) && !p.contains("list")) return true;
        return p.contains("create");
    }

    public JsonNode getComponents() {
        return swaggerRoot.path("components").path("schemas");
    }
}
