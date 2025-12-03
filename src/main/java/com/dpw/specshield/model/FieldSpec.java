package com.dpw.specshield.model;

import lombok.Data;

@Data
public class FieldSpec {
    private String name;
    private String type;          // string, integer, boolean, object, array
    private boolean required;
}

