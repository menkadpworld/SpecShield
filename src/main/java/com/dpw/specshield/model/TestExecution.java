package com.dpw.specshield.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class TestExecution {
    private String id;
    private LocalDateTime timestamp;
    private String scenario;
    private ExpectedResult expectedResult;
    private String result;
    private String resultDetails;
    private String contractPath;
    private String fullRequestPath;
    private String httpMethod;
    private RequestDetails requestDetails;

    @Data
    public static class RequestDetails {
        private Map<String, String> headers;
        private String payload;
        private String curl;
    }

}
