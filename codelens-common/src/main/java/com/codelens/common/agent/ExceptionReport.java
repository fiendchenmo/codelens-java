package com.codelens.common.agent;

/**
 * 异常报告数据类。
 * <p>
 * 描述方法可能抛出的异常信息：类型、处理方式、行号。
 */
public class ExceptionReport {

    private String type;
    private String handling;
    private int line;

    public ExceptionReport() {}

    public ExceptionReport(String type, String handling, int line) {
        this.type = type;
        this.handling = handling;
        this.line = line;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getHandling() { return handling; }
    public void setHandling(String handling) { this.handling = handling; }
    public int getLine() { return line; }
    public void setLine(int line) { this.line = line; }
}
