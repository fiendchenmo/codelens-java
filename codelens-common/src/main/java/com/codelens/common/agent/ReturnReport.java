package com.codelens.common.agent;

/**
 * 返回值报告数据类。
 * <p>
 * 描述方法的返回值信息：返回类型、业务含义。
 */
public class ReturnReport {

    private String type;
    private String businessMeaning;

    public ReturnReport() {}

    public ReturnReport(String type, String businessMeaning) {
        this.type = type;
        this.businessMeaning = businessMeaning;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getBusinessMeaning() { return businessMeaning; }
    public void setBusinessMeaning(String businessMeaning) { this.businessMeaning = businessMeaning; }
}
