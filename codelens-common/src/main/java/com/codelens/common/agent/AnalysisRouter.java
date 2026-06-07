package com.codelens.common.agent;

/**
 * 分析模式路由决策类。
 * <p>
 * 根据文件行数（LOC）和方法数量决定使用 SINGLE（单次 LLM 分析）还是
 * MULTI（多 Agent 分步分析）模式。
 * </p>
 *
 * <p>决策逻辑：</p>
 * <ul>
 *   <li>LOC ≤ 300 且 methods ≤ 8 → SINGLE（安全区）</li>
 *   <li>LOC ≤ 800 且 methods ≤ 15 → SINGLE（软上限，仍可用单次）</li>
 *   <li>其他 → MULTI（需分步分析）</li>
 * </ul>
 *
 * <p>阈值后续可从配置文件读取，当前为硬编码常量。</p>
 */
public class AnalysisRouter {

    public enum Mode {
        /** 单次 LLM 调用，适用于小文件 */
        SINGLE,
        /** 多 Agent 分步分析（SUMMARY → METHOD_ANALYSIS → 合并），适用于大文件 */
        MULTI
    }

    /** 安全区 LOC 上限 */
    private static final int SINGLE_LOC_LIMIT = 300;
    /** 安全区方法数上限 */
    private static final int SINGLE_METHOD_LIMIT = 8;

    /** 软上限 LOC */
    private static final int SINGLE_LOC_HARD_LIMIT = 800;
    /** 软上限方法数 */
    private static final int SINGLE_METHOD_HARD_LIMIT = 15;

    private AnalysisRouter() {}

    /**
     * 根据 LOC 和方法数决定分析模式。
     *
     * @param loc         文件行数（Lines of Code）
     * @param methodCount 文件中定义的方法总数（含构造器，不含匿名类方法）
     * @return SINGLE（单次分析）或 MULTI（多 Agent 分步分析）
     */
    public static Mode decide(int loc, int methodCount) {
        if (loc <= SINGLE_LOC_LIMIT && methodCount <= SINGLE_METHOD_LIMIT) {
            return Mode.SINGLE;
        }
        if (loc <= SINGLE_LOC_HARD_LIMIT && methodCount <= SINGLE_METHOD_HARD_LIMIT) {
            return Mode.SINGLE;
        }
        return Mode.MULTI;
    }

    /**
     * 返回决策的描述文本，方便日志输出。
     *
     * @param loc         文件行数
     * @param methodCount 方法总数
     * @param mode        决策结果
     * @return 格式如 "[AnalysisRouter] loc=150 methods=5 → SINGLE (安全区)"
     */
    public static String describe(int loc, int methodCount, Mode mode) {
        String reason;
        if (mode == Mode.SINGLE) {
            if (loc <= SINGLE_LOC_LIMIT && methodCount <= SINGLE_METHOD_LIMIT) {
                reason = "安全区";
            } else {
                reason = "软上限";
            }
        } else {
            reason = "超限";
        }
        return "[AnalysisRouter] loc=" + loc + " methods=" + methodCount + " → " + mode + " (" + reason + ")";
    }
}
