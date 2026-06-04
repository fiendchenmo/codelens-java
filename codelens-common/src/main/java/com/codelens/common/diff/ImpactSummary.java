package com.codelens.common.diff;

import com.codelens.common.models.ArchitectureLayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 影响分析摘要
 */
public class ImpactSummary {

    public int totalChangedFiles;
    public int totalChangedMethods;
    public int directImpactCount;
    public int indirectImpactCount;
    public Map<ArchitectureLayer, Integer> impactedLayerDist;
    public List<String> highRiskPaths;     // 影响路径中最需关注的 Top5
    public String note;                    // 降级说明等备注
    public int addedRiskCount;             // Diff 新增风险数
    public int eliminatedRiskCount;        // Diff 消除风险数
    public int addedLines;                 // Diff 新增行数
    public int deletedLines;               // Diff 删除行数
    public int modifiedLines;              // Diff 修改行数
    public List<RiskChange> riskChanges;   // 风险变更对比列表

    public ImpactSummary() {
        this.impactedLayerDist = new LinkedHashMap<>();
    }

    public ImpactSummary(int totalChangedFiles, int totalChangedMethods,
                         int directImpactCount, int indirectImpactCount,
                         Map<ArchitectureLayer, Integer> impactedLayerDist,
                         List<String> highRiskPaths) {
        this.totalChangedFiles = totalChangedFiles;
        this.totalChangedMethods = totalChangedMethods;
        this.directImpactCount = directImpactCount;
        this.indirectImpactCount = indirectImpactCount;
        this.impactedLayerDist = impactedLayerDist != null
                ? new LinkedHashMap<>(impactedLayerDist) : new LinkedHashMap<ArchitectureLayer, Integer>();
        this.highRiskPaths = highRiskPaths;
    }

    /**
     * 风险变更条目，记录单个风险的版本间变化状态。
     */
    public static class RiskChange {
        public String riskDescription;
        public String severity;
        public int line;
        public ChangeStatus changeStatus;
        public String version;
        public String suggestion;

        public RiskChange() {}
    }

    /**
     * 风险变更状态枚举。
     */
    public enum ChangeStatus {
        ELIMINATED,
        NEW,
        UNCHANGED,
        IMPROVED
    }
}
