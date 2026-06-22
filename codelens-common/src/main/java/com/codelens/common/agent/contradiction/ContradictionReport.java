package com.codelens.common.agent.contradiction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 文件级矛盾检测报告。
 * <p>
 * 由 {@link ContradictionDetector#detect} 生成，
 * 包含该文件所有方法分析中发现的矛盾点汇总。
 * </p>
 *
 * <p>整体矛盾评分计算规则：</p>
 * <ul>
 *   <li>每条 HIGH 矛盾 +0.3</li>
 *   <li>每条 MEDIUM 矛盾 +0.15</li>
 *   <li>每条 LOW 矛盾 +0.05</li>
 *   <li>上限 1.0</li>
 *   <li>只计算 status=CONTRADICTORY 的 findings</li>
 * </ul>
 */
public class ContradictionReport {

    /** 所有矛盾发现 */
    private List<ContradictionFinding> findings = new ArrayList<>();

    /** 文件级整体矛盾评分（0.0=无矛盾, 1.0=严重矛盾） */
    private double contradictionScore;

    /** 受矛盾影响的方法名集合 */
    private Set<String> affectedMethods;

    public ContradictionReport() {}

    // ─── getters / setters ──────────────────────────

    public List<ContradictionFinding> getFindings() { return findings; }
    public void setFindings(List<ContradictionFinding> findings) { this.findings = findings; }

    public double getContradictionScore() { return contradictionScore; }
    public void setContradictionScore(double contradictionScore) { this.contradictionScore = contradictionScore; }

    public Set<String> getAffectedMethods() { return affectedMethods; }
    public void setAffectedMethods(Set<String> affectedMethods) { this.affectedMethods = affectedMethods; }

    // ─── 计算 ────────────────────────────────────────

    /**
     * 计算整体矛盾评分。
     * <p>
     * 只计算 status=CONTRADICTORY 的 findings。
     * 同时填充 affectedMethods。
     * </p>
     */
    public void computeScore() {
        if (findings == null || findings.isEmpty()) {
            contradictionScore = 0.0;
            affectedMethods = Collections.emptySet();
            return;
        }
        double total = 0.0;
        Set<String> affected = new HashSet<String>();
        for (ContradictionFinding f : findings) {
            if (f.getStatus() != ContradictionFinding.Status.CONTRADICTORY) {
                continue;
            }
            switch (f.getSeverity()) {
                case HIGH:
                    total += 0.3;
                    break;
                case MEDIUM:
                    total += 0.15;
                    break;
                case LOW:
                    total += 0.05;
                    break;
                default:
                    break;
            }
            if (f.getSourceMethod() != null) {
                affected.add(f.getSourceMethod());
            }
        }
        contradictionScore = Math.min(1.0, total);
        affectedMethods = affected;
    }
}
