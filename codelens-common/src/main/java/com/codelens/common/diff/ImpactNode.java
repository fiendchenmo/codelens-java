package com.codelens.common.diff;

import com.codelens.common.models.ArchitectureLayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 受影响节点
 */
public class ImpactNode {

    public String className;
    public String methodName;           // 方法级影响时非空
    public ImpactLevel level;           // DIRECT, INDIRECT
    public ImpactConfidence confidence;  // HIGH, MEDIUM, LOW
    public int hopDistance;
    public List<String> impactPath;      // 回溯路径 ["A.m1", "B.m2", "C.m3"]
    public ArchitectureLayer layer;

    public ImpactNode() {
        this.impactPath = new ArrayList<>();
        this.layer = ArchitectureLayer.UNKNOWN;
    }

    public ImpactNode(String className, String methodName, ImpactLevel level,
                      ImpactConfidence confidence, int hopDistance,
                      List<String> impactPath, ArchitectureLayer layer) {
        this.className = className;
        this.methodName = methodName;
        this.level = level;
        this.confidence = confidence;
        this.hopDistance = hopDistance;
        this.impactPath = impactPath != null ? new ArrayList<>(impactPath) : new ArrayList<String>();
        this.layer = layer != null ? layer : ArchitectureLayer.UNKNOWN;
    }

    /**
     * 获取不可修改的影响路径视图。
     */
    public List<String> getImpactPath() {
        return impactPath != null
                ? Collections.unmodifiableList(impactPath)
                : Collections.emptyList();
    }
}
