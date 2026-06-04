package com.codelens.common.agent;

import com.codelens.common.models.ArchitectureLayer;

import java.util.List;
import java.util.Map;

/**
 * 聚合摘要的输出数据模型。
 * <p>
 * 由 LLM 生成，包含包/模块维度的整体摘要信息。
 * </p>
 */
public class AggregateSummaryOutput {

    private String packageName;
    private ArchitectureLayer architectureLayer;
    private String layerComposition;
    private String summary;
    private List<String> coreEntries;
    private List<String> coreResponsibilities;
    private List<CrossPackageDep> crossPackageDeps;
    private String riskOverview;
    private int totalFiles;
    private int totalMethods;
    /** 高风险数（由Validator从riskCategories反算，非LLM直接输出） */
    private int highRiskCount;
    /** 中风险数（由Validator从riskCategories反算，非LLM直接输出） */
    private int mediumRiskCount;
    private List<RiskCategoryEntry> riskCategories;
    private List<FileLayerEntry> fileLayers;
    /** 包级重构建议与风险提示（自然语言，2-4句话） */
    private String refactorOverview;

    // === 扩展统计字段（P1） ===
    private double avgComplexity;
    private double l1PassRate;
    private long analysisElapsedMs;
    private int lowRiskCount;
    private Map<String, Integer> complexityDistribution;
    private Map<String, Integer> visibilityDistribution;

    // === 扩展数据字段（P0） ===
    private List<ClassEntry> classEntries;
    private List<InternalDep> internalDeps;

    public AggregateSummaryOutput() {
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public ArchitectureLayer getArchitectureLayer() {
        return architectureLayer;
    }

    public void setArchitectureLayer(ArchitectureLayer architectureLayer) {
        this.architectureLayer = architectureLayer;
    }

    public String getLayerComposition() {
        return layerComposition;
    }

    public void setLayerComposition(String layerComposition) {
        this.layerComposition = layerComposition;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getCoreEntries() {
        return coreEntries;
    }

    public void setCoreEntries(List<String> coreEntries) {
        this.coreEntries = coreEntries;
    }

    public List<String> getCoreResponsibilities() {
        return coreResponsibilities;
    }

    public void setCoreResponsibilities(List<String> coreResponsibilities) {
        this.coreResponsibilities = coreResponsibilities;
    }

    public List<CrossPackageDep> getCrossPackageDeps() {
        return crossPackageDeps;
    }

    public void setCrossPackageDeps(List<CrossPackageDep> crossPackageDeps) {
        this.crossPackageDeps = crossPackageDeps;
    }

    public String getRiskOverview() {
        return riskOverview;
    }

    public void setRiskOverview(String riskOverview) {
        this.riskOverview = riskOverview;
    }

    public int getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
    }

    public int getTotalMethods() {
        return totalMethods;
    }

    public void setTotalMethods(int totalMethods) {
        this.totalMethods = totalMethods;
    }

    public int getHighRiskCount() {
        return highRiskCount;
    }

    public void setHighRiskCount(int highRiskCount) {
        this.highRiskCount = highRiskCount;
    }

    public int getMediumRiskCount() {
        return mediumRiskCount;
    }

    public void setMediumRiskCount(int mediumRiskCount) {
        this.mediumRiskCount = mediumRiskCount;
    }

    public List<RiskCategoryEntry> getRiskCategories() {
        return riskCategories;
    }

    public void setRiskCategories(List<RiskCategoryEntry> riskCategories) {
        this.riskCategories = riskCategories;
    }

    public List<FileLayerEntry> getFileLayers() {
        return fileLayers;
    }

    public void setFileLayers(List<FileLayerEntry> fileLayers) {
        this.fileLayers = fileLayers;
    }

    public String getRefactorOverview() {
        return refactorOverview;
    }

    public void setRefactorOverview(String refactorOverview) {
        this.refactorOverview = refactorOverview;
    }

    public double getAvgComplexity() { return avgComplexity; }
    public void setAvgComplexity(double avgComplexity) { this.avgComplexity = avgComplexity; }
    public double getL1PassRate() { return l1PassRate; }
    public void setL1PassRate(double l1PassRate) { this.l1PassRate = l1PassRate; }
    public long getAnalysisElapsedMs() { return analysisElapsedMs; }
    public void setAnalysisElapsedMs(long analysisElapsedMs) { this.analysisElapsedMs = analysisElapsedMs; }
    public int getLowRiskCount() { return lowRiskCount; }
    public void setLowRiskCount(int lowRiskCount) { this.lowRiskCount = lowRiskCount; }
    public Map<String, Integer> getComplexityDistribution() { return complexityDistribution; }
    public void setComplexityDistribution(Map<String, Integer> complexityDistribution) { this.complexityDistribution = complexityDistribution; }
    public Map<String, Integer> getVisibilityDistribution() { return visibilityDistribution; }
    public void setVisibilityDistribution(Map<String, Integer> visibilityDistribution) { this.visibilityDistribution = visibilityDistribution; }
    public List<ClassEntry> getClassEntries() { return classEntries; }
    public void setClassEntries(List<ClassEntry> classEntries) { this.classEntries = classEntries; }
    public List<InternalDep> getInternalDeps() { return internalDeps; }
    public void setInternalDeps(List<InternalDep> internalDeps) { this.internalDeps = internalDeps; }

    /**
     * 文件架构层条目，由 LLM 根据语义分析判断各文件所属架构层。
     */
    public static class FileLayerEntry {
        private String fileName;
        private String layer;  // ArchitectureLayer 枚举值字符串

        public FileLayerEntry() {
        }

        public FileLayerEntry(String fileName, String layer) {
            this.fileName = fileName;
            this.layer = layer;
        }

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getLayer() { return layer; }
        public void setLayer(String layer) { this.layer = layer; }
    }

    /**
     * 风险分类条目，由 LLM 根据语义分析归纳共性风险。
     */
    public static class RiskCategoryEntry {
        private String category;
        private String severity;    // HIGH / MEDIUM / LOW
        private String description;
        private List<String> affectedFiles;

        public RiskCategoryEntry() {
        }

        public RiskCategoryEntry(String category, String severity,
                                 String description, List<String> affectedFiles) {
            this.category = category;
            this.severity = severity;
            this.description = description;
            this.affectedFiles = affectedFiles;
        }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<String> getAffectedFiles() { return affectedFiles; }
        public void setAffectedFiles(List<String> affectedFiles) { this.affectedFiles = affectedFiles; }
    }

    /**
     * 类卡片条目，包级别分析中单个类的摘要信息。
     */
    public static class ClassEntry {
        private String className;
        private int methodCount;
        private int highRiskCount;
        private int mediumRiskCount;
        private double avgComplexity;
        private String filePath;

        public ClassEntry() {}

        public String getClassName() { return className; }
        public void setClassName(String className) { this.className = className; }
        public int getMethodCount() { return methodCount; }
        public void setMethodCount(int methodCount) { this.methodCount = methodCount; }
        public int getHighRiskCount() { return highRiskCount; }
        public void setHighRiskCount(int highRiskCount) { this.highRiskCount = highRiskCount; }
        public int getMediumRiskCount() { return mediumRiskCount; }
        public void setMediumRiskCount(int mediumRiskCount) { this.mediumRiskCount = mediumRiskCount; }
        public double getAvgComplexity() { return avgComplexity; }
        public void setAvgComplexity(double avgComplexity) { this.avgComplexity = avgComplexity; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
    }

    /**
     * 包内依赖条目，记录源类到目标类的调用关系及次数。
     */
    public static class InternalDep {
        private String sourceClass;
        private String targetClass;
        private int callCount;

        public InternalDep() {}

        public String getSourceClass() { return sourceClass; }
        public void setSourceClass(String sourceClass) { this.sourceClass = sourceClass; }
        public String getTargetClass() { return targetClass; }
        public void setTargetClass(String targetClass) { this.targetClass = targetClass; }
        public int getCallCount() { return callCount; }
        public void setCallCount(int callCount) { this.callCount = callCount; }
    }

    /**
     * 用于 Gson 反序列化的内部类型，与 {@link AggregateSummaryInput.CrossPackageDep} 结构相同，
     * 但在输出中作为独立数据类使用。
     */
    public static class CrossPackageDep {
        private String targetPackage;
        private java.util.List<String> viaMethods;
        private String direction;

        public CrossPackageDep() {
        }

        public String getTargetPackage() {
            return targetPackage;
        }

        public void setTargetPackage(String targetPackage) {
            this.targetPackage = targetPackage;
        }

        public java.util.List<String> getViaMethods() {
            return viaMethods;
        }

        public void setViaMethods(java.util.List<String> viaMethods) {
            this.viaMethods = viaMethods;
        }

        public String getDirection() {
            return direction;
        }

        public void setDirection(String direction) {
            this.direction = direction;
        }
    }
}
