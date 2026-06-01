package com.codelens.common.diff;

import java.util.ArrayList;
import java.util.List;

/**
 * 影响分析报告
 */
public class ImpactReport {

    public String commitHash;
    public List<ChangedFile> changes;     // 变更点
    public List<ImpactNode> impacts;      // 受影响节点
    public ImpactSummary summary;         // 摘要统计

    public ImpactReport() {
        this.changes = new ArrayList<>();
        this.impacts = new ArrayList<>();
    }

    public ImpactReport(String commitHash, List<ChangedFile> changes,
                        List<ImpactNode> impacts, ImpactSummary summary) {
        this.commitHash = commitHash;
        this.changes = changes != null ? new ArrayList<>(changes) : new ArrayList<ChangedFile>();
        this.impacts = impacts != null ? new ArrayList<>(impacts) : new ArrayList<ImpactNode>();
        this.summary = summary;
    }
}
