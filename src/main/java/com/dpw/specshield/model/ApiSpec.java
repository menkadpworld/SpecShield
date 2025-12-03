package com.dpw.specshield.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class ApiSpec {
    private String name;       // e.g., "POST /resource/makes"
    private String path;
    private String method;
    private JsonNode schema;
}


