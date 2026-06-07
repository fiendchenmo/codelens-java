package com.codelens.common.agent;

/**
 * 分析模式路由决策类。
 * <p>
 * 根据文件行数（LOC）、方法数量以及类类型决定使用 SINGLE（单次 LLM 分析）还是
 * MULTI（多 Agent 分步分析）模式。
 * </p>
 *
 * <p>决策逻辑：</p>
 * <ul>
 *   <li>DTO/VO/BO 类 → SINGLE（简单结构降级）</li>
 *   <li>枚举类 → SINGLE</li>
 *   <li>纯接口（无 default 方法）→ SINGLE</li>
 *   <li>LOC ≤ 300 且 methods ≤ 8 → SINGLE（安全区）</li>
 *   <li>LOC ≤ 800 且 methods ≤ 15 → SINGLE（软上限）</li>
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
     * <p>
     * 此方法保留原始签名，不涉及类类型判断。
     *
     * @param loc         文件行数（Lines of Code）
     * @param methodCount 文件中定义的方法总数
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
     * 根据 LOC、方法数以及类类型信息决定分析模式。
     * <p>
     * 优先级：DTO/VO/BO 降级 → 枚举 → 纯接口 → 常规 {@link #decide(int, int)} 路由。
     *
     * @param loc              文件行数
     * @param methodCount      方法总数
     * @param className        类全限定名，用于检测 DTO/VO/BO 后缀
     * @param isEnum           是否为枚举类
     * @param isInterface      是否为接口
     * @param hasDefaultMethod 接口中是否含有 default 方法（仅 isInterface=true 时有意义）
     * @return SINGLE（单次分析）或 MULTI（多 Agent 分步分析）
     */
    public static Mode decide(int loc, int methodCount, String className,
                               boolean isEnum, boolean isInterface, boolean hasDefaultMethod) {
        // 1. DTO/VO/BO 类 → 简单结构降级
        if (isDtoLikeName(className)) {
            return Mode.SINGLE;
        }
        // 2. 枚举 → SINGLE
        if (isEnum) {
            return Mode.SINGLE;
        }
        // 3. 纯接口（无 default 方法）→ SINGLE
        if (isInterface && !hasDefaultMethod) {
            return Mode.SINGLE;
        }
        // 4. 常规路由
        return decide(loc, methodCount);
    }

    /**
     * 根据类名后缀判断是否为 DTO/VO/BO 等简单数据结构类。
     *
     * @param className 类名（全限定名或简单名均可）
     * @return true 如果类名以 DTO/VO/BO/Entity/Model 结尾（大小写不敏感）
     */
    public static boolean isDtoLikeName(String className) {
        if (className == null || className.isEmpty()) return false;
        String upper = className.toUpperCase();
        return upper.endsWith("DTO")
                || upper.endsWith("VO")
                || upper.endsWith("BO")
                || upper.endsWith("ENTITY")
                || upper.endsWith("MODEL");
    }

    /**
     * 返回决策的描述文本，方便日志输出。
     * <p>
     * 自动根据 mode 和阈值推断原因。
     *
     * @param loc         文件行数
     * @param methodCount 方法总数
     * @param mode        决策结果
     * @return 格式如 "[AnalysisRouter] loc=150 methods=5 → SINGLE (安全区)"
     */
    public static String describe(int loc, int methodCount, Mode mode) {
        return describe(loc, methodCount, mode, null);
    }

    /**
     * 返回决策的描述文本，支持指定原因。
     *
     * @param loc         文件行数
     * @param methodCount 方法总数
     * @param mode        决策结果
     * @param reason      自定义原因，为 null 时自动推断
     * @return 格式如 "[AnalysisRouter] loc=150 methods=5 → SINGLE (安全区)"
     */
    public static String describe(int loc, int methodCount, Mode mode, String reason) {
        if (reason == null) {
            if (mode == Mode.SINGLE) {
                if (loc <= SINGLE_LOC_LIMIT && methodCount <= SINGLE_METHOD_LIMIT) {
                    reason = "安全区";
                } else {
                    reason = "软上限";
                }
            } else {
                reason = "超限";
            }
        }
        return "[AnalysisRouter] loc=" + loc + " methods=" + methodCount + " → " + mode + " (" + reason + ")";
    }
}
