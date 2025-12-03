package com.dpw.specshield.model;

import lombok.Data;
import java.util.Map;

@Data
public class SchemaModel {
    private String type;                      // object, array, string, integer
    private Map<String, FieldSpec> fields;    // only for object
}

