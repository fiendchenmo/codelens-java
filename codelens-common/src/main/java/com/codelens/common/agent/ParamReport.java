package com.codelens.common.agent;

/**
 * 参数报告数据类。
 * <p>
 * 描述方法的参数信息：名称、类型、使用场景、示例值。
 */
public class ParamReport {

    private String name;
    private String type;
    private String usage;
    private String sample;

    public ParamReport() {}

    public ParamReport(String name, String type, String usage, String sample) {
        this.name = name;
        this.type = type;
        this.usage = usage;
        this.sample = sample;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getUsage() { return usage; }
    public void setUsage(String usage) { this.usage = usage; }
    public String getSample() { return sample; }
    public void setSample(String sample) { this.sample = sample; }
}
