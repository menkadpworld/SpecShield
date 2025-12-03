package com.dpw.specshield.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Component
public class TestIdMapping {

    private final Map<String, Integer> idMapping = new HashMap<>();

    public TestIdMapping() {
        loadMapping();
    }

    private void loadMapping() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = getClass().getClassLoader().getResourceAsStream("test-id-mapping.json");

            if (is == null) {
                throw new RuntimeException("Cannot find test-id-mapping.json in resources.");
            }

            JsonNode root = mapper.readTree(is);

            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();

                String key = entry.getKey(); // e.g. "vendors", "vehicles", "product"
                JsonNode valueNode = entry.getValue();
                Integer id = valueNode.path("id").asInt();

                idMapping.put(key, id);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load test-id-mapping.json", e);
        }
    }

    /**
     * Substring match to find ID based on API path.
     * Example:
     *  path = "/resource/vendors"  → matches "vendors"
     */
    public Integer getIdForEndpoint(String path) {
        if (path == null) return null;

        for (Map.Entry<String, Integer> entry : idMapping.entrySet()) {
            String keyword = entry.getKey();  // "vendors"
            if (path.contains(keyword)) {     // substring match
                return entry.getValue();      // return mapped ID
            }
        }

        return null;
    }
}
