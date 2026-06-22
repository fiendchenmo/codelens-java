package com.codelens.common.agent.contradiction;

import com.codelens.common.agent.AnalysisReport;
import com.codelens.common.agent.L1Call;
import com.codelens.common.agent.L1Evidence;
import com.codelens.common.agent.L2Confidence;
import com.codelens.common.agent.MethodReport;
import com.codelens.common.agent.RiskItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 矛盾检测引擎。
 * <p>
 * 在多 Agent 架构的合并阶段后执行，对 SUMMARY Agent 与多个
 * METHOD_ANALYSIS Agent 的输出做跨 Agent 交叉验证，
 * 检测自相矛盾的结论并生成 {@link ContradictionReport}。
 * </p>
 *
 * <p>检测规则（按执行顺序）：</p>
 * <ol>
 *   <li><b>C4 字段自相矛盾</b> — complexity 与 complexityValue 不一致</li>
 *   <li><b>C2 摘要-细节冲突</b> — SUMMARY.complexity 与多数方法 complexity 矛盾</li>
 *   <li><b>C1 调用图互斥</b> — A 声称调用了 B，但 B 未确认被 A 调用</li>
 *   <li><b>C3 风险-证据矛盾</b> — risk 行号指向注释/空行</li>
 * </ol>
 *
 * <p>核心原则：</p>
 * <ul>
 *   <li>不丢弃任何 Agent 输出 — 矛盾不删除结论，只降置信度</li>
 *   <li>可溯源 — 每条矛盾发现都追溯到具体 Agent 输出 + 矛盾证据</li>
 *   <li>幂等 — 对同一输入，检测结果确定，不依赖执行顺序</li>
 *   <li>calledBy 为空时标记 INCOMPLETE，不降置信度（避免 C1 误报）</li>
 * </ul>
 */
public class ContradictionDetector {

    /**
     * 从合并后的 AnalysisReport 检测矛盾。
     *
     * @param report      合并后的分析报告
     * @param sourceLines 源文件各行内容（用于 C3 注释行检测），可为 null
     * @return 矛盾检测报告
     */
    public ContradictionReport detect(AnalysisReport report, String[] sourceLines) {
        ContradictionReport result = new ContradictionReport();
        List<ContradictionFinding> findings = new ArrayList<ContradictionFinding>();

        if (report == null || report.getMethods() == null || report.getMethods().isEmpty()) {
            result.setFindings(findings);
            result.computeScore();
            return result;
        }

        List<MethodReport> methods = report.getMethods();

        // 构建方法名 → MethodReport 索引（用于 C1 交叉引用）
        Map<String, MethodReport> methodIndex = buildMethodIndex(methods);

        // 按规则顺序执行检测
        // C4: 字段自相矛盾（最简单，先扫）
        findings.addAll(detectFieldSelfContradiction(methods));

        // C2: 摘要-细节冲突
        findings.addAll(detectSummaryDetailConflict(report, methods));

        // C1: 调用图互斥（需要方法间交叉引用）
        findings.addAll(detectCallGraphMismatch(methods, methodIndex));

        // C3: 风险-证据矛盾（需要源码行）
        findings.addAll(detectRiskEvidenceContradiction(methods, sourceLines));

        result.setFindings(findings);
        result.computeScore();
        return result;
    }

    // ─── C4: 字段自相矛盾 ──────────────────────────

    /**
     * 检测 complexity 枚举值与 complexityValue 数值不一致。
     *
     * <p>映射关系：LOW=1-3, MEDIUM=4-6, HIGH=7-10</p>
     */
    List<ContradictionFinding> detectFieldSelfContradiction(List<MethodReport> methods) {
        List<ContradictionFinding> findings = new ArrayList<ContradictionFinding>();
        for (MethodReport m : methods) {
            String complexity = m.getComplexity();
            int value = m.getComplexityValue();
            if (complexity == null || value <= 0) {
                continue;
            }
            String upper = complexity.toUpperCase().trim();
            boolean inconsistent = false;
            String expectedRange = "";
            if ("HIGH".equals(upper) && value <= 3) {
                inconsistent = true;
                expectedRange = "7-10";
            } else if ("LOW".equals(upper) && value >= 8) {
                inconsistent = true;
                expectedRange = "1-3";
            } else if ("MEDIUM".equals(upper) && (value < 4 || value > 6)) {
                // MEDIUM 不在需求明确列出，但同样属于字段矛盾，检测到了也报告
                inconsistent = true;
                expectedRange = "4-6";
            }
            if (inconsistent) {
                String desc = "method " + m.getMethodName()
                        + " complexity=" + upper
                        + " but complexityValue=" + value
                        + " (expected range " + expectedRange + ")";
                findings.add(new ContradictionFinding(
                        ContradictionFinding.ContradictionType.FIELD_SELF_CONTRADICTION,
                        ContradictionFinding.Severity.LOW,
                        m.getMethodName(), null,
                        -0.1,
                        desc,
                        "complexity=" + upper + " vs complexityValue=" + value,
                        ContradictionFinding.Status.CONTRADICTORY
                ));
            }
        }
        return findings;
    }

    // ─── C2: 摘要-细节冲突 ──────────────────────────

    /**
     * 检测 SUMMARY 给出的整体复杂度与各方法复杂度分布矛盾。
     *
     * <p>触发条件：SUMMARY.complexity="LOW" 但 ≥50% 方法 complexity="HIGH"</p>
     */
    List<ContradictionFinding> detectSummaryDetailConflict(AnalysisReport report,
                                                            List<MethodReport> methods) {
        List<ContradictionFinding> findings = new ArrayList<ContradictionFinding>();
        String overallComplexity = report.getOverallComplexity();
        if (!"LOW".equalsIgnoreCase(overallComplexity)) {
            return findings;
        }
        if (methods.isEmpty()) {
            return findings;
        }

        int highCount = 0;
        List<String> highMethods = new ArrayList<String>();
        for (MethodReport m : methods) {
            if ("HIGH".equalsIgnoreCase(m.getComplexity())) {
                highCount++;
                highMethods.add(m.getMethodName() + "()=HIGH");
            }
        }

        double ratio = (double) highCount / methods.size();
        if (ratio >= 0.5) {
            String desc = "SUMMARY claims LOW complexity, but "
                    + highCount + "/" + methods.size()
                    + " methods are HIGH: " + highMethods;
            findings.add(new ContradictionFinding(
                    ContradictionFinding.ContradictionType.SUMMARY_DETAIL_CONFLICT,
                    ContradictionFinding.Severity.HIGH,
                    null, null,
                    -0.3,
                    desc,
                    "overallComplexity=LOW vs " + highCount + " HIGH methods",
                    ContradictionFinding.Status.CONTRADICTORY
            ));
        }
        return findings;
    }

    // ─── C1: 调用图互斥 ─────────────────────────────

    /**
     * 检测同文件内方法间调用关系矛盾。
     *
     * <p>逻辑：A.calls 包含 targetMethod B，但 B.calledBy 存在且非空
     * 且不含 A 的方法名 → 记录矛盾。
     * B.calledBy 不存在或为空 → 标记 INCOMPLETE（不降置信度）。</p>
     *
     * <p>仅检测同文件内的方法互引。跨文件调用不参与。</p>
     */
    List<ContradictionFinding> detectCallGraphMismatch(List<MethodReport> methods,
                                                        Map<String, MethodReport> methodIndex) {
        List<ContradictionFinding> findings = new ArrayList<ContradictionFinding>();
        for (MethodReport a : methods) {
            L1Evidence l1A = a.getL1Evidence();
            if (l1A == null || l1A.getCalls() == null) {
                continue;
            }
            for (L1Call call : l1A.getCalls()) {
                String targetSimpleName = normalizeMethodName(call.getTarget());
                if (targetSimpleName == null || targetSimpleName.isEmpty()) {
                    continue;
                }

                MethodReport b = findMethodBySimpleName(methodIndex, targetSimpleName);
                if (b == null) {
                    // 跨文件调用，跳过
                    continue;
                }
                // 跳过自调用（递归）
                if (b.getMethodName() != null && b.getMethodName().equals(a.getMethodName())) {
                    continue;
                }

                L1Evidence l1B = b.getL1Evidence();
                if (l1B == null || l1B.getCalledBy() == null || l1B.getCalledBy().isEmpty()) {
                    // calledBy 数据不完整，标记 INCOMPLETE，不降置信度
                    findings.add(new ContradictionFinding(
                            ContradictionFinding.ContradictionType.CALL_GRAPH_MISMATCH,
                            ContradictionFinding.Severity.MEDIUM,
                            a.getMethodName(), b.getMethodName(),
                            0.0,  // INCOMPLETE 不惩罚
                            a.getMethodName() + "() claims call to " + b.getMethodName()
                                    + "(), but " + b.getMethodName() + "() calledBy is empty (INCOMPLETE data)",
                            "call target: " + call.getTarget() + ", calledBy: empty",
                            ContradictionFinding.Status.INCOMPLETE
                    ));
                } else {
                    // calledBy 存在且非空，检查是否包含 A
                    if (!containsCaller(l1B.getCalledBy(), a.getMethodName())) {
                        findings.add(new ContradictionFinding(
                                ContradictionFinding.ContradictionType.CALL_GRAPH_MISMATCH,
                                ContradictionFinding.Severity.MEDIUM,
                                a.getMethodName(), b.getMethodName(),
                                -0.2,
                                a.getMethodName() + "() L" + call.getLine()
                                        + " claims call to " + b.getMethodName()
                                        + "(), but " + b.getMethodName()
                                        + "() calledBy does not include " + a.getMethodName(),
                                "call target: " + call.getTarget()
                                        + " @ line " + call.getLine()
                                        + ", calledBy: " + l1B.getCalledBy(),
                                ContradictionFinding.Status.CONTRADICTORY
                        ));
                    }
                }
            }
        }
        return findings;
    }

    // ─── C3: 风险-证据矛盾 ──────────────────────────

    /**
     * 检测风险项行号对应的源码行是否为注释或空行。
     *
     * <p>当前实现：检测 risk.line 是否指向注释行或空行。
     * P0 trace 的 verificationStatus 检测待 P0 完成后接入。</p>
     *
     * <p>跨方法聚合：同一文件 ≥3 个 risk 被标记为源码行矛盾时，
     * 额外在 contradictionScore 中体现（通过多条 finding 累加）。</p>
     *
     * @param methods     方法报告列表
     * @param sourceLines 源文件各行内容（0-indexed），可为 null
     * @return C3 矛盾发现列表
     */
    List<ContradictionFinding> detectRiskEvidenceContradiction(List<MethodReport> methods,
                                                                String[] sourceLines) {
        List<ContradictionFinding> findings = new ArrayList<ContradictionFinding>();
        if (sourceLines == null || sourceLines.length == 0) {
            return findings;
        }

        for (MethodReport m : methods) {
            if (m.getRisks() == null) {
                continue;
            }
            for (RiskItem risk : m.getRisks()) {
                int line = risk.getLine();
                if (line <= 0) {
                    continue;
                }
                // sourceLines 是 0-indexed，risk.line 是 1-indexed
                if (line > sourceLines.length) {
                    continue;
                }
                String sourceLine = sourceLines[line - 1];
                if (sourceLine == null) {
                    continue;
                }
                String trimmed = sourceLine.trim();
                if (isCommentOrBlank(trimmed)) {
                    String desc = m.getMethodName() + "() risk \""
                            + truncate(risk.getDescription(), 60)
                            + "\" @ line " + line
                            + " points to comment/blank: \""
                            + truncate(trimmed, 40) + "\"";
                    findings.add(new ContradictionFinding(
                            ContradictionFinding.ContradictionType.RISK_EVIDENCE_CONTRADICTION,
                            ContradictionFinding.Severity.HIGH,
                            m.getMethodName(), null,
                            -0.5,
                            desc,
                            "sourceLine[" + (line - 1) + "] = \""
                                    + truncate(trimmed, 80) + "\"",
                            ContradictionFinding.Status.CONTRADICTORY
                    ));
                }
            }
        }
        return findings;
    }

    // ─── 工具方法 ───────────────────────────────────

    /**
     * 构建方法简单名 → MethodReport 的索引。
     */
    private Map<String, MethodReport> buildMethodIndex(List<MethodReport> methods) {
        Map<String, MethodReport> index = new HashMap<String, MethodReport>();
        for (MethodReport m : methods) {
            if (m.getMethodName() != null && !m.getMethodName().isEmpty()) {
                index.put(m.getMethodName(), m);
            }
        }
        return index;
    }

    /**
     * 按简单名从索引中查找方法。
     * <p>
     * 如果存在多个同名方法（重载），返回第一个匹配的。
     * </p>
     */
    private MethodReport findMethodBySimpleName(Map<String, MethodReport> index, String simpleName) {
        return index.get(simpleName);
    }

    /**
     * 规范化调用目标为简单方法名。
     * <ul>
     *   <li>"deptMapper.selectDeptList" → "selectDeptList"</li>
     *   <li>"this.selectDeptList" → "selectDeptList"</li>
     *   <li>"selectDeptList" → "selectDeptList"</li>
     *   <li>null → null</li>
     * </ul>
     */
    static String normalizeMethodName(String target) {
        if (target == null || target.isEmpty()) {
            return null;
        }
        // 去除 "this." 前缀
        String cleaned = target;
        if (cleaned.startsWith("this.")) {
            cleaned = cleaned.substring(5);
        }
        // 取最后一个点之后的部分
        int lastDot = cleaned.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < cleaned.length() - 1) {
            return cleaned.substring(lastDot + 1);
        }
        return cleaned;
    }

    /**
     * 检查 calledBy 列表中是否包含给定的调用方方法名。
     * <p>
     * calledBy 条目格式为 "fully.qualified.ClassName.methodName"，
     * 取最后一个点之后的简单方法名与 callerMethodName 比较。
     * </p>
     */
    static boolean containsCaller(List<String> calledBy, String callerMethodName) {
        if (calledBy == null || callerMethodName == null) {
            return false;
        }
        for (String entry : calledBy) {
            if (entry == null) {
                continue;
            }
            String simple = normalizeMethodName(entry);
            if (callerMethodName.equals(simple)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断源码行是否为注释或空白行。
     */
    static boolean isCommentOrBlank(String trimmedLine) {
        if (trimmedLine.isEmpty()) {
            return true;
        }
        // 单行注释
        if (trimmedLine.startsWith("//")) {
            return true;
        }
        // 块注释开始
        if (trimmedLine.startsWith("/*")) {
            return true;
        }
        // 块注释中间行（以 * 开头）
        if (trimmedLine.startsWith("*") && !trimmedLine.startsWith("*/")) {
            return true;
        }
        // 块注释结束行
        if (trimmedLine.equals("*/") || trimmedLine.startsWith("*/")) {
            return true;
        }
        return false;
    }

    /**
     * 截断字符串到指定长度。
     */
    static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}
