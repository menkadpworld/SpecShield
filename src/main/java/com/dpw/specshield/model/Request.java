package com.dpw.specshield.model;

import lombok.Data;
import java.util.Map;

@Data
public class Request {
    private Map<String, String> headers;
    private Map<String, String> queryParams;
    private Map<String, String> pathParams;
    private Object body;
}