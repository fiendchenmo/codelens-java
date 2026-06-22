package com.codelens.common.agent;

import com.codelens.common.agent.contradiction.ContradictionReport;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import com.codelens.common.models.ArchitectureLayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合并分析报告。
 * <p>
 * 将 SUMMARY 输出的类级信息与 METHOD_ANALYSIS 输出的方法级信息合并。
 */
public class AnalysisReport {

    private String className;
    private String stereotype;
    private String summary;
    private String frameworkDesc;
    private List<FieldReport> fields;
    private List<MethodReport> methods;
    private List<String> dependencies;
    private String overallComplexity;
    private Map<String, Object> metadata;
    private List<AggregateSummaryOutput> packageSummaries;
    private AggregateSummaryOutput moduleSummary;

    /** 文件级矛盾检测报告（P1 白盒矛盾检测） */
    private ContradictionReport contradictionReport;

    public AnalysisReport() {}

    public AnalysisReport(String className, String stereotype, List<MethodReport> methods,
                          List<String> dependencies, String overallComplexity) {
        this.className = className;
        this.stereotype = stereotype;
        this.methods = methods;
        this.dependencies = dependencies;
        this.overallComplexity = overallComplexity;
        this.metadata = new LinkedHashMap<>();
    }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getStereotype() { return stereotype; }
    public void setStereotype(String stereotype) { this.stereotype = stereotype; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getFrameworkDesc() { return frameworkDesc; }
    public void setFrameworkDesc(String frameworkDesc) { this.frameworkDesc = frameworkDesc; }
    public List<FieldReport> getFields() { return fields; }
    public void setFields(List<FieldReport> fields) { this.fields = fields; }
    public List<MethodReport> getMethods() { return methods; }
    public void setMethods(List<MethodReport> methods) { this.methods = methods; }
    public List<String> getDependencies() { return dependencies; }
    public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }
    public String getOverallComplexity() { return overallComplexity; }
    public void setOverallComplexity(String overallComplexity) { this.overallComplexity = overallComplexity; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public List<AggregateSummaryOutput> getPackageSummaries() { return packageSummaries; }
    public void setPackageSummaries(List<AggregateSummaryOutput> packageSummaries) { this.packageSummaries = packageSummaries; }

    public AggregateSummaryOutput getModuleSummary() { return moduleSummary; }
    public void setModuleSummary(AggregateSummaryOutput moduleSummary) { this.moduleSummary = moduleSummary; }

    public ContradictionReport getContradictionReport() { return contradictionReport; }
    public void setContradictionReport(ContradictionReport contradictionReport) { this.contradictionReport = contradictionReport; }

    /**
     * 序列化为 JSON。
     */
    public String toJson() {
        return new GsonBuilder().setPrettyPrinting().create().toJson(this);
    }
}
