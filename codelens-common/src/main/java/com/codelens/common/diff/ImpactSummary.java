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
}
