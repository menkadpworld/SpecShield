package com.dpw.specshield.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Component
@Slf4j
public class SwaggerParser {

    private final JsonNode swaggerRoot;

    public SwaggerParser(@Value("${swagger.url}") String swaggerJsonPath) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        if (swaggerJsonPath.startsWith("classpath:")) {
            String path = swaggerJsonPath.substring(10);
            InputStream is = getClass().getClassLoader().getResourceAsStream(path);
            if (is == null)
                throw new RuntimeException("NOT found in classpath: " + path);
            swaggerRoot = mapper.readTree(is);
        } else if (swaggerJsonPath.startsWith("http")) {
            swaggerRoot = mapper.readTree(new URL(swaggerJsonPath));
        } else {
            swaggerRoot = mapper.readTree(new File(swaggerJsonPath));
        }
    }

    public Map<String, JsonNode> getSchemas() {
        Map<String, JsonNode> schemaMap = new HashMap<>();
        JsonNode pathsNode = swaggerRoot.get("paths");
        if (pathsNode == null) return schemaMap;

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

                if (isCreateEndpoint(path, operation, httpMethod)) continue;

                JsonNode requestBody = operation.get("requestBody");
                if (requestBody != null) {
                    JsonNode schema = requestBody.at("/content/application~1json/schema");
                    if (!schema.isMissingNode()) {
                        String key = httpMethod + " " + path;
                        schemaMap.put(key, schema);
                    }
                }
            }
        }

        return schemaMap;
    }

    public JsonNode getComponents() {
        return swaggerRoot.path("components").path("schemas");
    }

    private boolean isCreateEndpoint(String path, JsonNode operation, String httpMethod) {
        String pathLower = path.toLowerCase();

        // Exclude PATCH endpoints
        if ("PATCH".equals(httpMethod)) {
            return true;
        }

        // Skip "create" endpoints or POST endpoints that are NOT a "list"
        if ("POST".equals(httpMethod)) {
            boolean isList = pathLower.contains("list");
            return !isList; // if POST and not a list, skip
        }

        // Optionally, skip any explicit "create" paths
        return pathLower.contains("create");
    }
}
