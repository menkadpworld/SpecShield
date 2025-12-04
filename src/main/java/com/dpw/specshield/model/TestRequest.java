package com.dpw.specshield.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TestRequest {
    private Map<String, String> headers;
    private Map<String, String> pathParams;
    private Map<String, String> queryParams;
    private JsonNode body; // generated payload
}

