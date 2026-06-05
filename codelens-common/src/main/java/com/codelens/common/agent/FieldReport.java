package com.codelens.common.agent;

/**
 * 字段报告数据类。
 * <p>
 * 描述类的字段信息：注入类型、数据类型、行号等。
 */
public class FieldReport {

    private String name;
    private String type;
    private String injectType;
    private String description;
    private int line;

    public FieldReport() {}

    public FieldReport(String name, String type, String injectType, String description, int line) {
        this.name = name;
        this.type = type;
        this.injectType = injectType;
        this.description = description;
        this.line = line;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getInjectType() { return injectType; }
    public void setInjectType(String injectType) { this.injectType = injectType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getLine() { return line; }
    public void setLine(int line) { this.line = line; }
}
