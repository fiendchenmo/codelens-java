package com.codelens.common.validators.l3;

public class KnownConstraint {
    private final String type;
    private final String value;

    public KnownConstraint(String type, String value) {
        this.type = type;
        this.value = value;
    }

    public String getType() { return type; }
    public String getValue() { return value; }
}
