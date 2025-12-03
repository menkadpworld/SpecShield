package com.dpw.specshield.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class ApiSpec {
    private String name;       // e.g., "POST /resource/makes"
    private String path;
    private String method;
    private JsonNode schema;
    private Map<String, String> queryParams = new HashMap<>();
    private JsonNode responseSchema;
}


