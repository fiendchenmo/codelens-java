package com.codelens.common.agent;

import com.codelens.common.models.ArchitectureLayer;

import java.util.List;
import java.util.Map;

/**
 * 聚合摘要的输入数据模型。
 * <p>
 * 包含包名、包内各文件的摘要条目、跨包依赖关系、以及架构分层分布。
 * </p>
 */
public class AggregateSummaryInput {

    private String packageName;
    private List<FileSummaryEntry> fileSummaries;
    private List<CrossPackageDep> crossPackageDeps;
    private Map<ArchitectureLayer, Integer> layerDistribution;

    public AggregateSummaryInput() {
    }

    public AggregateSummaryInput(String packageName,
                                  List<FileSummaryEntry> fileSummaries,
                                  List<CrossPackageDep> crossPackageDeps,
                                  Map<ArchitectureLayer, Integer> layerDistribution) {
        this.packageName = packageName;
        this.fileSummaries = fileSummaries;
        this.crossPackageDeps = crossPackageDeps;
        this.layerDistribution = layerDistribution;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public List<FileSummaryEntry> getFileSummaries() {
        return fileSummaries;
    }

    public void setFileSummaries(List<FileSummaryEntry> fileSummaries) {
        this.fileSummaries = fileSummaries;
    }

    public List<CrossPackageDep> getCrossPackageDeps() {
        return crossPackageDeps;
    }

    public void setCrossPackageDeps(List<CrossPackageDep> crossPackageDeps) {
        this.crossPackageDeps = crossPackageDeps;
    }

    public Map<ArchitectureLayer, Integer> getLayerDistribution() {
        return layerDistribution;
    }

    public void setLayerDistribution(Map<ArchitectureLayer, Integer> layerDistribution) {
        this.layerDistribution = layerDistribution;
    }

    // ========================================================================
    // 嵌套数据类
    // ========================================================================

    /**
     * 单文件摘要条目。
     */
    public static class FileSummaryEntry {
        private String fileName;
        private ArchitectureLayer layer;
        private String summary;
        private String framework;
        private String overallDesign;
        private String riskSummary;
        private List<String> coreMethods;
        private List<String> calledByExternal;

        public FileSummaryEntry() {
        }

        public FileSummaryEntry(String fileName, ArchitectureLayer layer,
                                 String summary, String framework,
                                 String overallDesign, String riskSummary,
                                 List<String> coreMethods,
                                 List<String> calledByExternal) {
            this.fileName = fileName;
            this.layer = layer;
            this.summary = summary;
            this.framework = framework;
            this.overallDesign = overallDesign;
            this.riskSummary = riskSummary;
            this.coreMethods = coreMethods;
            this.calledByExternal = calledByExternal;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public ArchitectureLayer getLayer() {
            return layer;
        }

        public void setLayer(ArchitectureLayer layer) {
            this.layer = layer;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public String getFramework() {
            return framework;
        }

        public void setFramework(String framework) {
            this.framework = framework;
        }

        public String getOverallDesign() {
            return overallDesign;
        }

        public void setOverallDesign(String overallDesign) {
            this.overallDesign = overallDesign;
        }

        public String getRiskSummary() {
            return riskSummary;
        }

        public void setRiskSummary(String riskSummary) {
            this.riskSummary = riskSummary;
        }

        public List<String> getCoreMethods() {
            return coreMethods;
        }

        public void setCoreMethods(List<String> coreMethods) {
            this.coreMethods = coreMethods;
        }

        public List<String> getCalledByExternal() {
            return calledByExternal;
        }

        public void setCalledByExternal(List<String> calledByExternal) {
            this.calledByExternal = calledByExternal;
        }
    }

    /**
     * 跨包依赖条目。
     */
    public static class CrossPackageDep {
        private String targetPackage;
        private List<String> viaMethods;
        private String direction; // "outgoing" | "incoming"

        public CrossPackageDep() {
        }

        public CrossPackageDep(String targetPackage,
                                List<String> viaMethods,
                                String direction) {
            this.targetPackage = targetPackage;
            this.viaMethods = viaMethods;
            this.direction = direction;
        }

        public String getTargetPackage() {
            return targetPackage;
        }

        public void setTargetPackage(String targetPackage) {
            this.targetPackage = targetPackage;
        }

        public List<String> getViaMethods() {
            return viaMethods;
        }

        public void setViaMethods(List<String> viaMethods) {
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
