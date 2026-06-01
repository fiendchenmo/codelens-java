package com.codelens.common.diff;

import com.codelens.common.analyzer.ArchitectureLayerDetector;
import com.codelens.common.callindex.CallIndex;
import com.codelens.common.callindex.CallRecord;
import com.codelens.common.models.ArchitectureLayer;

import java.util.*;

/**
 * 影响分析器 — BFS 扩散引擎。
 * <p>
 * 从变更方法出发，沿调用链反向扩散（queryByCallee），
 * 生成所有受影响节点的 ImpactReport。
 * </p>
 */
public class ImpactAnalyzer {

    /** 变更文件数超过此阈值时自动降级为包级分析 */
    private static final int FILE_COUNT_THRESHOLD = 50;

    /** 单节点最大调用方数（防止单节点扩散过宽） */
    private static final int DEFAULT_MAX_CALLERS_PER_NODE = 30;
    /** 总影响节点上限（防止搜索空间指数膨胀） */
    private static final int DEFAULT_MAX_TOTAL_IMPACTS = 200;

    private final CallIndex callIndex;
    private final int maxHops;
    private final int maxCallersPerNode;
    private final int maxTotalImpacts;

    /**
     * @param callIndex 调用索引（可为 null，表示 CallIndex 缺失降级）
     * @param maxHops   最大扩散跳数
     */
    public ImpactAnalyzer(CallIndex callIndex, int maxHops) {
        this(callIndex, maxHops, DEFAULT_MAX_CALLERS_PER_NODE, DEFAULT_MAX_TOTAL_IMPACTS);
    }

    /**
     * @param callIndex         调用索引（可为 null，表示 CallIndex 缺失降级）
     * @param maxHops           最大扩散跳数
     * @param maxCallersPerNode 单节点最大调用方数
     * @param maxTotalImpacts   总影响节点上限
     */
    public ImpactAnalyzer(CallIndex callIndex, int maxHops,
                          int maxCallersPerNode, int maxTotalImpacts) {
        this.callIndex = callIndex;
        this.maxHops = maxHops > 0 ? maxHops : 3;
        this.maxCallersPerNode = maxCallersPerNode > 0 ? maxCallersPerNode : DEFAULT_MAX_CALLERS_PER_NODE;
        this.maxTotalImpacts = maxTotalImpacts > 0 ? maxTotalImpacts : DEFAULT_MAX_TOTAL_IMPACTS;
    }

    /**
     * 执行影响分析。
     *
     * @param changes    变更文件列表
     * @param commitHash 基准 commit
     * @return 影响分析报告
     */
    public ImpactReport analyze(List<ChangedFile> changes, String commitHash) {
        if (changes == null) {
            changes = new ArrayList<>();
        }

        // 大变更降级：超 50 文件 → 只做包级分析
        if (changes.size() > FILE_COUNT_THRESHOLD) {
            return analyzePackageLevel(changes, commitHash);
        }

        // CallIndex 缺失降级
        if (callIndex == null) {
            return analyzeWithoutCallIndex(changes, commitHash);
        }

        // ===== BFS 扩散 =====
        // visited 跟踪已处理的源方法（用于去重）
        Set<String> visited = new LinkedHashSet<>();
        // bestHop 用于多路径去重：保留最短 hop
        Map<String, ImpactNode> nodeMap = new LinkedHashMap<>();
        // BFS 队列
        LinkedList<BfsNode> queue = new LinkedList<>();

        // Step 1: 初始化队列 — 变更方法入队
        for (ChangedFile file : changes) {
            if (file.significance == ChangeSignificance.LOW) {
                continue;
            }
            if (file.changedMethods == null || file.changedMethods.isEmpty()) {
                continue;
            }
            for (ChangedMethod method : file.changedMethods) {
                // ADDED 方法不参与反向扩散（无人调用新方法）
                if (method.changeType == ChangeType.ADDED) {
                    continue;
                }
                String key = method.className + "." + method.methodName;
                visited.add(key);

                BfsNode seed = new BfsNode();
                seed.className = method.className;
                seed.methodName = method.methodName;
                seed.hop = 0;
                seed.impactPath = new ArrayList<>();
                seed.impactPath.add(key);
                // 如果是 DELETED 方法，在路径中标注
                if (method.changeType == ChangeType.DELETED) {
                    seed.impactPath.set(0, key + " [DELETED]");
                }
                queue.add(seed);
            }
        }

        // Step 2: BFS 扩散
        while (!queue.isEmpty()) {
            // ② 总影响节点上限守卫
            if (nodeMap.size() >= maxTotalImpacts) {
                break;
            }

            BfsNode current = queue.poll();

            if (current.hop >= maxHops) {
                continue;
            }

            // 查询谁调用了这个方法（SQL LIMIT + 防御性截断）
            List<CallRecord> callers;
            try {
                callers = callIndex.queryByCallee(current.className, current.methodName, maxCallersPerNode);
            } catch (Exception e) {
                // 查询失败则跳过此节点
                continue;
            }

            // ① 防御性截断：即使 SQL LIMIT 因实现差异未生效，Java 侧再保一道
            if (callers != null && callers.size() > maxCallersPerNode) {
                callers = new ArrayList<>(callers.subList(0, maxCallersPerNode));
            }

            if (callers == null || callers.isEmpty()) {
                continue;
            }

            for (CallRecord caller : callers) {
                String callerKey = caller.getCallerClass() + "." + caller.getCallerMethod();

                // 已访问跳过
                if (visited.contains(callerKey)) {
                    continue;
                }

                visited.add(callerKey);

                int newHop = current.hop + 1;
                ImpactLevel level = (current.hop == 0) ? ImpactLevel.DIRECT : ImpactLevel.INDIRECT;
                ImpactConfidence confidence = computeConfidence(caller.getCallType(), newHop);

                // 构建影响路径：从当前路径追加调用方
                List<String> newPath = new ArrayList<>(current.impactPath);
                newPath.add(callerKey);

                // 检测架构层
                String simpleName = simpleClassName(caller.getCallerClass());
                String packageName = extractPackage(caller.getCallerClass());
                ArchitectureLayer layer = ArchitectureLayerDetector.detectClassLayer(
                        null, simpleName, packageName);

                ImpactNode node = new ImpactNode(
                        caller.getCallerClass(),
                        caller.getCallerMethod(),
                        level,
                        confidence,
                        newHop,
                        newPath,
                        layer
                );

                // 去重：同一方法多条路径时保留最短 hop
                // visited 已保证不重复处理，直接放入
                nodeMap.put(callerKey, node);

                // 入队继续扩散
                BfsNode next = new BfsNode();
                next.className = caller.getCallerClass();
                next.methodName = caller.getCallerMethod();
                next.hop = newHop;
                next.impactPath = newPath;
                queue.add(next);
            }
        }

        // Step 3: 构建结果
        List<ImpactNode> impacts = new ArrayList<>(nodeMap.values());

        // Step 4: 生成摘要
        ImpactSummary summary = buildSummary(changes, impacts);

        return new ImpactReport(commitHash, changes, impacts, summary);
    }

    // ==================== 包级分析（大变更降级）====================

    /**
     * 包级分析：变更文件过多时降级，仅列出变更文件，不做方法级扩散。
     */
    private ImpactReport analyzePackageLevel(List<ChangedFile> changes, String commitHash) {
        ImpactSummary summary = buildSummary(changes, new ArrayList<ImpactNode>());
        summary.note = "变更量过大（" + changes.size() + "文件），已降级为包级分析";
        return new ImpactReport(commitHash, changes, new ArrayList<ImpactNode>(), summary);
    }

    /**
     * CallIndex 缺失降级：只做文件级分析，无扩散。
     */
    private ImpactReport analyzeWithoutCallIndex(List<ChangedFile> changes, String commitHash) {
        ImpactSummary summary = buildSummary(changes, new ArrayList<ImpactNode>());
        summary.note = "CallIndex 缺失，仅文件级分析";
        return new ImpactReport(commitHash, changes, new ArrayList<ImpactNode>(), summary);
    }

    // ==================== 置信度计算 ====================

    /**
     * 根据调用类型和跳数计算置信度。
     * <p>
     * 基础映射：
     * <ul>
     *   <li>DIRECT → HIGH</li>
     *   <li>SPRING_INJECTION → MEDIUM</li>
     *   <li>REFLECTION → LOW</li>
     *   <li>其他 → MEDIUM</li>
     * </ul>
     * 间接影响（hop ≥ 2）时，HIGH 降级为 MEDIUM。
     * </p>
     */
    private ImpactConfidence computeConfidence(String callType, int hop) {
        ImpactConfidence base;
        if ("DIRECT".equals(callType)) {
            base = ImpactConfidence.HIGH;
        } else if ("SPRING_INJECTION".equals(callType)) {
            base = ImpactConfidence.MEDIUM;
        } else if ("REFLECTION".equals(callType)) {
            base = ImpactConfidence.LOW;
        } else {
            base = ImpactConfidence.MEDIUM;
        }
        // 间接影响（hop≥2）：HIGH 降级为 MEDIUM
        if (hop >= 2 && base == ImpactConfidence.HIGH) {
            return ImpactConfidence.MEDIUM;
        }
        return base;
    }

    // ==================== 摘要生成 ====================

    /**
     * 构建影响摘要。
     */
    private ImpactSummary buildSummary(List<ChangedFile> changes, List<ImpactNode> impacts) {
        ImpactSummary summary = new ImpactSummary();

        summary.totalChangedFiles = countChangedFiles(changes);
        summary.totalChangedMethods = countChangedMethods(changes);
        summary.directImpactCount = 0;
        summary.indirectImpactCount = 0;

        // 统计层分布
        Map<ArchitectureLayer, Integer> layerDist = new LinkedHashMap<>();
        for (ImpactNode node : impacts) {
            if (node.level == ImpactLevel.DIRECT) {
                summary.directImpactCount++;
            } else {
                summary.indirectImpactCount++;
            }
            ArchitectureLayer layer = node.layer;
            layerDist.put(layer, layerDist.getOrDefault(layer, 0) + 1);
        }
        summary.impactedLayerDist = layerDist;

        // 高风险路径 Top5
        summary.highRiskPaths = computeHighRiskPaths(impacts);

        return summary;
    }

    /**
     * 计算高风险路径（按 hop+confidence 排序，取 Top5）。
     * <p>
     * 影响评级：
     * <ul>
     *   <li>hop=1 && confidence=HIGH → HIGH (🔴)</li>
     *   <li>hop=1 && confidence≤MEDIUM → MEDIUM (🟡)</li>
     *   <li>hop=2 && confidence=HIGH → MEDIUM (🟡)</li>
     *   <li>其他 → LOW (🟢)</li>
     * </ul>
     * </p>
     */
    private List<String> computeHighRiskPaths(List<ImpactNode> impacts) {
        // 筛选并排序：高风险优先
        List<ImpactNode> sorted = new ArrayList<>(impacts);
        Collections.sort(sorted, (a, b) -> {
            int ra = riskScore(a);
            int rb = riskScore(b);
            if (ra != rb) return rb - ra; // 降序
            return a.hopDistance - b.hopDistance; // 同风险，短路径优先
        });

        List<String> paths = new ArrayList<>();
        int count = 0;
        for (ImpactNode node : sorted) {
            if (count >= 5) break;
            // 只取 HIGH(3) 和 MEDIUM(2)，跳过 LOW(1) 以下
            int score = riskScore(node);
            if (score <= 1) break;
            String prefix = score >= 3 ? "🔴 " : "🟡 ";
            String pathStr = prefix + String.join(" → ", node.impactPath);
            paths.add(pathStr);
            count++;
        }
        return paths;
    }

    /**
     * 风险分数：3=🔴HIGH, 2=🟡MEDIUM, 1=🟢LOW, 0=忽略
     */
    private int riskScore(ImpactNode node) {
        if (node.hopDistance == 1 && node.confidence == ImpactConfidence.HIGH) return 3;  // 🔴
        if (node.hopDistance == 1 && node.confidence.ordinal() <= ImpactConfidence.MEDIUM.ordinal()) return 2; // 🟡
        if (node.hopDistance == 2 && node.confidence == ImpactConfidence.HIGH) return 2;  // 🟡
        if (node.hopDistance <= maxHops) return 1;  // 🟢
        return 0;
    }

    // ==================== 统计辅助 ====================

    /**
     * 统计有实际变更方法的文件数（排除 LOW significance 文件）。
     */
    private int countChangedFiles(List<ChangedFile> changes) {
        int count = 0;
        for (ChangedFile f : changes) {
            if (f.significance != ChangeSignificance.LOW) {
                count++;
            }
        }
        return count;
    }

    /**
     * 统计变更方法总数。
     */
    private int countChangedMethods(List<ChangedFile> changes) {
        int count = 0;
        for (ChangedFile f : changes) {
            if (f.changedMethods != null && f.significance != ChangeSignificance.LOW) {
                count += f.changedMethods.size();
            }
        }
        return count;
    }

    // ==================== 工具方法 ====================

    /**
     * 从全限定类名中提取简单类名。
     */
    static String simpleClassName(String fullClassName) {
        if (fullClassName == null || fullClassName.isEmpty()) return "";
        int lastDot = fullClassName.lastIndexOf('.');
        if (lastDot >= 0) {
            return fullClassName.substring(lastDot + 1);
        }
        return fullClassName;
    }

    /**
     * 从全限定类名中提取包名。
     */
    static String extractPackage(String fullClassName) {
        if (fullClassName == null || fullClassName.isEmpty()) return "";
        int lastDot = fullClassName.lastIndexOf('.');
        if (lastDot >= 0) {
            return fullClassName.substring(0, lastDot);
        }
        return "";
    }

    // ==================== BFS 内部节点 ====================

    /**
     * BFS 队列中的扩散节点。
     */
    private static class BfsNode {
        String className;
        String methodName;
        int hop;
        List<String> impactPath;
    }
}
