package com.codelens.common.agent;

import java.util.List;

/**
 * L1 一级证据数据类。
 */
public class L1Evidence {

    private List<String> calls;
    private List<String> calledBy;
    private List<String> fieldsUsed;

    public L1Evidence() {}

    public L1Evidence(List<String> calls, List<String> calledBy, List<String> fieldsUsed) {
        this.calls = calls;
        this.calledBy = calledBy;
        this.fieldsUsed = fieldsUsed;
    }

    public List<String> getCalls() { return calls; }
    public void setCalls(List<String> calls) { this.calls = calls; }
    public List<String> getCalledBy() { return calledBy; }
    public void setCalledBy(List<String> calledBy) { this.calledBy = calledBy; }
    public List<String> getFieldsUsed() { return fieldsUsed; }
    public void setFieldsUsed(List<String> fieldsUsed) { this.fieldsUsed = fieldsUsed; }
}
