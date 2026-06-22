package com.codelens.common.agent.contradiction;

import com.codelens.common.agent.AnalysisReport;
import com.codelens.common.agent.L2Confidence;
import com.codelens.common.agent.MethodReport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 矛盾注入器。
 * <p>
 * 将 {@link ContradictionDetector} 检测到的矛盾发现
 * 写回 {@link AnalysisReport}、{@link MethodReport} 和 {@link L2Confidence}，
 * 使下游（插件端/CLI 端）能够获取并渲染矛盾信息。
 * </p>
 *
 * <p>注入策略：</p>
 * <ul>
 *   <li>按 sourceMethod 匹配，将 findings 写入对应 MethodReport.contradictionFindings</li>
 *   <li>C2（摘要矛盾）直接写入 AnalysisReport.contradictionReport</li>
 *   <li>更新 L2Confidence.overallScore = max(0.1, originalScore + penalty)</li>
 *   <li>L2Confidence.contradictionPenalty 累加所有惩罚值</li>
 * </ul>
 *
 * <p>置信度地板值：0.1，避免置信度归零导致结果无意义。</p>
 */
public class ContradictionInjector {

    /** L2 置信度地板值 */
    public static final double MIN_L2_SCORE = 0.1;

    /**
     * 将矛盾报告注入 AnalysisReport。
     * <p>
     * 注入操作：<ol>
     *   <li>将 contradictionReport 写入 AnalysisReport</li>
     *   <li>将 findings 分配给各 MethodReport.contradictionFindings</li>
     *   <li>更新各方法的 L2Confidence（累加 contradictionPenalty，地板值 0.1）</li>
     * </ol>
     * </p>
     *
     * @param report        合并后的分析报告（会被修改）
     * @param contradiction 矛盾检测报告
     */
    public void inject(AnalysisReport report, ContradictionReport contradiction) {
        if (report == null || contradiction == null) {
            return;
        }

        // 1. 写入文件级矛盾报告
        report.setContradictionReport(contradiction);

        // 2. 构建 sourceMethod → findings 映射
        Map<String, List<ContradictionFinding>> findingMap = buildFindingMap(contradiction);

        // 3. 注入到各方法
        if (report.getMethods() != null) {
            for (MethodReport m : report.getMethods()) {
                if (m.getMethodName() == null) {
                    continue;
                }
                List<ContradictionFinding> methodFindings = findingMap.get(m.getMethodName());
                if (methodFindings != null && !methodFindings.isEmpty()) {
                    m.setContradictionFindings(new ArrayList<ContradictionFinding>(methodFindings));
                }

                // 4. 更新 L2Confidence
                updateL2Confidence(m, methodFindings);
            }
        }
    }

    /**
     * 将 findings 按 sourceMethod 分组。
     * C2 类型的 finding（sourceMethod 为 null）不分配。
     */
    private Map<String, List<ContradictionFinding>> buildFindingMap(ContradictionReport contradiction) {
        Map<String, List<ContradictionFinding>> map = new HashMap<String, List<ContradictionFinding>>();
        if (contradiction.getFindings() == null) {
            return map;
        }
        for (ContradictionFinding f : contradiction.getFindings()) {
            if (f.getSourceMethod() == null) {
                continue; // C2 等无 sourceMethod 的 finding 不分配到方法
            }
            if (f.getStatus() != ContradictionFinding.Status.CONTRADICTORY) {
                continue; // INCOMPLETE 不注入方法
            }
            List<ContradictionFinding> list = map.get(f.getSourceMethod());
            if (list == null) {
                list = new ArrayList<ContradictionFinding>();
                map.put(f.getSourceMethod(), list);
            }
            list.add(f);
        }
        return map;
    }

    /**
     * 更新方法的 L2Confidence。
     * <ul>
     *   <li>累加所有 contradictionPenalty 到 overallScore</li>
     *   <li>设置 contradictionPenalty 字段</li>
     *   <li>地板值 MIN_L2_SCORE (0.1)</li>
     * </ul>
     */
    private void updateL2Confidence(MethodReport method, List<ContradictionFinding> findings) {
        L2Confidence l2 = method.getL2Confidence();
        if (l2 == null) {
            return;
        }
        double totalPenalty = 0.0;
        if (findings != null) {
            for (ContradictionFinding f : findings) {
                totalPenalty += f.getConfidencePenalty();
            }
        }
        l2.setContradictionPenalty(totalPenalty);

        // 更新 overallScore
        double originalScore = l2.getOverallScore();
        double newScore = Math.max(MIN_L2_SCORE, originalScore + totalPenalty);
        l2.setOverallScore(newScore);
    }
}
