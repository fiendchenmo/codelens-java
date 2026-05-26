// SYNC_VERSION: 2026-05-26-v1
// 维护方:喵呜(CLI端)，prompt/校验器相关由喵呜拍板
// 同步说明:两端共用 JSON Schema & 核心规则，修改需双方确认
// C-3: SchemaVersion枚举 + JSON_SCHEMA版本化

package com.codelens.common.models;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 代码元数据结构
 * LLM 返回的 JSON 结构定义 + 核心分析规则
 */
public class CodeMetaData {

    /**
     * 依赖项结构
     */
    public static class Dependency {
        public String name;        // 依赖名称
        public String type;       // 依赖类型:字段注入/静态方法调用/构造注入
        public String line;       // 所在行号
        public String description; // 描述(合并 reason -> description)
    }

    /**
     * 风险项结构
     */
    public static class Risk {
        public String type;       // 风险类型:SECURITY|PERFORMANCE|MAINTAINABILITY
        public String severity;   // 严重程度:HIGH|MEDIUM|LOW
        public String description; // 风险描述
        public String line;       // 所在行号
        public String impact;     // 对系统的影响
        public String suggestion;  // 修复建议
    }

    /**
     * 关键方法结构
     */
    public static class KeyMethod {
        public String name;       // 方法名
        public String line;       // 所在行号
        public String signature;  // 方法签名
        public String visibility; // 可见性:public|private|protected
        public String description; // 方法功能描述(合并 notes -> description)
    }

    // ==================== JSON Schema 版本化 (C-3) ====================

    // ==================== JSON Schema 版本化 (C-3) ====================

    /**
     * LLM 输出 JSON Schema Map (用于 prompt 中的结构化输出约束)
     * 
     * V2 — 8顶层字段（旧版）
     * V3 — 4顶层字段（新版）：summary, framework, fields, methods
     */
    private static final Map<SchemaVersion, String> JSON_SCHEMA_MAP = new LinkedHashMap<>();
    
    static {
        // V2 Schema (向后兼容)
        JSON_SCHEMA_MAP.put(SchemaVersion.V2, buildV2Schema());
        // V3 Schema (新版)
        JSON_SCHEMA_MAP.put(SchemaVersion.V3, buildV3Schema());
    }
    
    /**
     * 获取默认版本的JSON Schema（V2，向后兼容）
     * @deprecated 使用 {@link #getSchema(SchemaVersion)} 明确指定版本
     * @deprecated 使用 getSchema(SchemaVersion) 明确指定版本
     */
    @Deprecated
    public static final String JSON_SCHEMA = buildV2Schema();
    
    /**
     * 根据版本获取JSON Schema
     * @param version Schema版本
     * @return 对应版本的JSON Schema字符串
     */
    public static String getSchema(SchemaVersion version) {
        if (version == null) {
            return JSON_SCHEMA_MAP.get(SchemaVersion.V2);
        }
        String schema = JSON_SCHEMA_MAP.get(version);
        return schema != null ? schema : JSON_SCHEMA_MAP.get(SchemaVersion.V2);
    }
    
    /**
     * 获取所有可用版本
     */
    public static SchemaVersion[] getAvailableVersions() {
        return JSON_SCHEMA_MAP.keySet().toArray(new SchemaVersion[0]);
    }

    /**
     * 构建V2版本的JSON Schema（向后兼容）
     */
    private static String buildV2Schema() {
        return ""
            + "{\n"
            + "  \"summary\": \"一句话功能摘要\",\n"
            + "  \"design_intent\": \"设计意图分析:这个类在整个系统中的角色，它协调了哪些外部资源\",\n"
            + "  \"class_analysis\": \"数据流描述:从输入到输出的关键数据流转路径，只写数据流，不要重复summary和design_intent\",\n"
            + "  \"dependencies\": [\n"
            + "    {\"name\": \"依赖对象\", \"type\": \"依赖类型(字段注入|静态方法调用|构造注入)\", "
            + "\"line\": 行号, \"description\": \"描述(1-2句)\"}\n"
            + "  ],\n"
            + "  \"risks\": [\n"
            + "    {\"type\": \"SECURITY|PERFORMANCE|MAINTAINABILITY\", "
            + "\"description\": \"风险描述，必须基于代码事实\", "
            + "\"line\": 行号, \"severity\": \"HIGH|MEDIUM|LOW\", "
            + "\"impact\": \"影响面\", "
            + "\"suggestion\": \"修复建议\"}\n"
            + "  ],\n"
            + "  \"keyMethods\": [\n"
            + "    {\"name\": \"方法名\", \"line\": 行号, "
            + "\"signature\": \"方法签名(含参数类型)\", "
            + "\"visibility\": \"public|private|protected\", "
            + "\"annotations\": \"方法注解(如@Transactional/@Async/@Scheduled)\", "
            + "\"calls\": [\"调用的方法列表\"], "
            + "\"description\": \"方法功能描述\"}\n"
            + "  ],\n"
            + "  \"framework_integration\": \"框架集成分析:本类使用了哪些框架(Spring/Quartz/MyBatis等)，"
            + "框架的关键调用链是什么，框架的行为如何影响本类的逻辑正确性\",\n"
            + "}";
    }
    
    /**
     * 构建V3版本的JSON Schema
     * 
     * V3字段映射：
     * - dependencies → fields + methods.calls
     * - risks → methods.risks  
     * - keyMethods → methods（统一不分key/non-key）
     * - architecture_issues → 删除（合入methods.risks）
     * - design_intent/class_analysis → 合并进summary
     * - framework_integration → 顶层framework
     */
    private static String buildV3Schema() {
        return ""
            + "{\n"
            + "  \"summary\": \"功能摘要:一句话概括本类的职责，融合设计意图与数据流转路径\",\n"
            + "  \"framework\": \"框架集成:本类使用的框架(Spring/Quartz/MyBatis等)及关键调用链、框架行为对逻辑的影响\",\n"
            + "  \"fields\": [\n"
            + "    {\"name\": \"字段名\", \"type\": \"字段类型\", \"line\": 行号, \"injectType\": \"依赖类型(AUTOWIRED|RESOURCE|INJECT|STATIC)\", "
            + "\"description\": \"字段用途描述\"}\n"
            + "  ],\n"
            + "  \"methods\": [\n"
            + "    {\n"
            + "      \"name\": \"方法名\",\n"
            + "      \"line\": 行号,\n"
            + "      \"complexity\": \"复杂度(LOW|MEDIUM|HIGH)\",\n"
            + "      \"signature\": \"方法签名(含参数类型)\",\n"
            + "      \"visibility\": \"可见性(public|private|protected)\",\n"
            + "      \"annotations\": \"关键注解(@Transactional/@Async/@Scheduled等)\",\n"
            + "      \"description\": \"方法功能描述\",\n"
            + "      \"params\": [{\"name\": \"参数名\", \"type\": \"参数类型\", \"usage\": \"参数用途\", \"sample\": \"调用示例\"}],\n"
            + "      \"logic_summary\": \"核心逻辑:用1-2句话描述方法的业务逻辑\",\n"
            + "      \"calls\": [{\"target\": \"被调方法(类名.方法名)\", \"line\": 行号, \"type\": \"调用类型(same_file|cross_file|static)\"}],\n"
            + "      \"return\": {\"type\": \"返回类型\", \"business_meaning\": \"返回值的业务含义\"},\n"
            + "      \"risks\": [{\"type\": \"SECURITY|PERFORMANCE|MAINTAINABILITY\", \"description\": \"风险描述\", "
            + "\"line\": 行号, \"severity\": \"HIGH|MEDIUM|LOW\", \"impact\": \"影响面\", \"suggestion\": \"修复建议\"}],\n"
            + "      \"exceptions\": [{\"type\": \"异常类型\", \"handling\": \"异常处理方式\", \"line\": 行号}],\n"
            + "      \"called_by\": [{\"caller\": \"调用方(类名.方法名)\", \"line\": 行号}]\n"
            + "    }\n"
            + "  ]\n"
            + "}";
    }

    // ==================== 核心分析规则 ====================

    /**
     * 两端共用的核心分析规则. 
     * 每个端在此基础上补充自己的特有上下文(如插件端的 PSI 标签说明). 
     */
    public static final String CORE_RULES = ""
            + "核心分析规则(两端共用):\n"
            + "1. 每条 risks 和 dependencies 必须指向具体代码行号(line 字段)\n"
            + "2. dependencies 必须包含所有依赖注入的字段，以及所有第三方和框架类的静态方法调用;"
            + "同一静态方法在不同业务场景中应按用途拆分为多条，同场景同方法可合并为一条标注首个行号. "
            + "不要列日志类(Logger/Log)，不要列 getter/setter，不要列纯值对象类(String/Integer/List 等)\n"
            + "3. risks 必须基于代码事实，不可猜测，不可写「需确认」类模糊描述;"
            + "每条 risk 必须包含 impact 字段说明影响面. "
            + "severity 判断标准:不可恢复的数据损坏/状态永久不一致=高，"
            + "未捕获异常导致程序崩溃=高，事务无法补偿的外部系统状态变更=高;"
            + "被 @Transactional 兜住会回滚的异常=中，逻辑错误导致校验被跳过=中，可恢复的异常=中，代码风格问题=低\n"
            + "4. 必须检查安全风险(不得遗漏):路径遍历(文件路径拼接), "
            + "SQL 注入(表名/列名拼接传入 Mapper 时若无法确认使用 #{} 参数化查询应标为风险), "
            + "空指针链(链式调用未判空，说明什么输入会触发 null), 命令注入, 不安全反序列化, 硬编码密钥\n"
            + "5. 检查异常处理对事务的影响:catch 块吞异常(仅 log.error 未重新抛出)"
            + "会导致 Spring 事务不回滚-这是事务方法的严重问题;"
            + "特别关注 @Transactional 方法中的异常处理模式\n"
            + "6. 检查跨资源一致性:当一个方法同时操作 DB 和外部系统(调度器/缓存/消息队列)，"
            + "必须分析两阶段操作的失败场景-DB 成功但外部系统失败时状态是否一致，是否有补偿/回滚机制. "
            + "这类跨资源一致性问题写入 risks 中标注具体代码行\n"
            + "7. @Transactional 自调用绕过代理:检查方法内是否有 this.xxx() 自调用，"
            + "被调方法上的 @Transactional 会被跳过(Spring AOP 代理模式下 this 不走代理). "
            + "如需事务一致性，必须通过注入的代理对象调用(@Autowired self)\n"
            + "8. 所有架构建议需标注前提条件:若依赖于运行时配置(如 Quartz JobStore 类型)，"
            + "\"必须明确标注. 例如:\"假设使用 RAMJobStore(RuoYi 默认)->此方案可行\"，\""
            + "\"若使用 JDBC JobStore->前置条件已变更，建议重新评估\". "
            + "\"对框架默认值标注即可，对可配置项必须标注假设值\n\""
            + "9. 初始化时序问题(必查项):检查 @PostConstruct / @EventListener / InitializingBean "
            + "标注的 init 方法. 如果 init 方法中有多步操作(如 clear -> selectAll -> 逐条 create)，"
            + "中途失败会导致状态不一致(部分操作已执行，部分未执行). "
            + "同时检查 init 方法的执行时机-是否依赖外部资源(如 scheduler)，该资源在 init 执行时是否已完全就绪\n"
            + "10. 复合操作原子性(必查项):如果一个方法先执行 DB 操作(如 insert/update)再调用外部系统"
            + "(如 Quartz API)，检查 DB 操作成功后外部调用失败的场景-"
            + "这会导致「幽灵记录」(DB 有但外部无). 标记为事务/逻辑类风险，"
            + "建议使用事务性发件箱模式或重新排序. 区分 DB->外部 和 外部->DB 的顺序差异:"
            + "前者产生幽灵记录，后者更安全\n"
            + "12. framework_integration 不得为空！必须分析本类使用的框架的关键行为和前提条件:"
            + "框架方法的副作用, 框架异常处理机制, 框架与 DB 的事务关系. "
            + "例如:如果用了 Quartz，必须分析 JobStore 类型(RAMJobStore 内存存储 vs JobStoreTX/JDBC 持久化)"
            + "对一致性的影响-若是 JDBC JobStore 则调度器操作和 DB 操作共享同一数据库，跨资源一致性问题可能不存在;"
            + "如果是 RAMJobStore 则是真正的跨资源问题. 如果用了 Spring 事务，"
            + "要分析 @Transactional 的传播行为和回滚条件\n"
            + "13. 只输出 JSON，不要 markdown 代码块包裹，不要加任何前缀/后缀说明文字\n"
            + "14. 同一类安全风险只列一条 risk，在 description 中列举所有涉及方法，只标首个入口行号\n"
            + "15. keyMethods 的 description 字段保持精简，只写关键发现，不要重复 purpose 已涵盖的内容. "
            + "优先保证 risks 的完整性\n"
            + "16. class_analysis 必须用箭头(->)分隔数据流步骤，格式: 输入 -> 步骤1 -> 步骤2 -> ... -> 输出. "
            + "每个步骤应标注关键方法名或操作类型，如: DB查询(selectById) -> 缓存写入(redis.set) -> 返回结果. "
            + "多分支用分号分隔，如: 输入 -> 分支A: ... ; 分支B: ... -> 输出\n"
+ "17. dependencies 仅包含字段级别的依赖（字段注入/构造注入/静态方法调用），方法级别的调用关系放入 keyMethods.calls 数组。不要在 dependencies 中出现 type 为 \"method_call\" 或类似方法调用的条目\n"
+ "18. dependencies[].name 必须是字段名（如 \"queryRefBillService\"），不能写全限定类名（如 \"com.stream.ecs.bill.service.IEcsBillMainService\"）、接口全名（如 \"IEcsBillMainService\"）或方法调用（如 \"billInfoCmd.queryBillInfoById()\"）。字段名 = 源码中 @Autowired/@Resource 注入的变量名\n"
+ "19. dependencies[] 只列核心业务依赖（Service/Handler/Manager/Cmd 等业务组件），不列工具类（StringUtil/BeanUtil/BigDecimalUtil/DateUtils/JSONUtil/ListUtil/MapUtil/CollectionUtils/Arrays/Collections 等）、不列 JDK 标准库（Map/List/String/BigDecimal/Integer 等）、不列框架基类（IBaseService/BaseMapper 等）。每个类的业务依赖建议控制在 15-30 个\n"
+ "20. [代码结构底图] 中的字段和行号是代码真实提取的，你的分析必须以此为基准。dependencies[].name 必须使用底图中的字段名，dependencies[].line 必须使用底图中的行号。如果底图中没有某个字段，不要在 dependencies 中编造\n"
+ "21. keyMethods[].calls 必须是对象数组，每个对象包含 method(方法名,不含括号)、line(调用行号)、type(调用类型: same_file/cross_file)。不能写成字符串数组如 [\"selectById()\"]，必须写 [{\"method\":\"selectById\",\"line\":100,\"type\":\"cross_file\"}]\n"
+ "22. dependencies[].type 只能是以下枚举值之一：\"field\"（依赖注入字段，如 @Autowired 注入的 service）或 \"method_call\"（方法调用，如 obj.method()）。不能写 injection/dependency/service/cross_file/internal 等非标值";

    /**
     * 标签规范定义
     */
    public static class Tags {
        // 解析标签
        public static final String PSI_SAME_FILE = "[PSI_SAME_FILE]";           // PSI 同文件解析
        public static final String PSI_CROSS_FILE = "[PSI_CROSS_FILE]";         // PSI 跨文件解析
        public static final String JP_UNRESOLVED = "[CODELENS_JP_UNRESOLVED]";   // JavaParser 无法解析
        public static final String JP_FALLBACK = "[CODELENS_JP_FALLBACK]";      // JavaParser 回退模式

        // 校验标签
        public static final String L1_PASSED = "[L1_PASSED]";                   // L1 证据校验通过
        public static final String L1_FAILED = "[L1_FAILED]";                   // L1 证据校验失败
        public static final String L1_SKIPPED = "[L1_SKIPPED]";                 // L1 校验跳过

        // 置信度标签
        public static final String CONF_CERTAIN = "[CERTAIN]";                  // 置信度:确定
        public static final String CONF_HIGH = "[HIGH]";                        // 置信度:高
        public static final String CONF_MEDIUM = "[MEDIUM]";                    // 置信度:中
        public static final String CONF_LOW = "[LOW]";                          // 置信度:低

        // 特殊标记
        public static final String HALLUCINATION = "[HALLUCINATION]";            // 疑似幻觉
        public static final String NEED_REVIEW = "[NEED_REVIEW]";               // 需要人工审核
    }
}
