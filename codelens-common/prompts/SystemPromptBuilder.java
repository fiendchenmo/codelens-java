// SYNC_VERSION: 2026-05-16-v1
// 维护方：喵呜（CLI端），prompt/校验器相关由喵呜拍板
// 同步说明：核心分析规则从 CLI 端抽取，供插件端复用

package com.codelens.common.prompts;

import com.codelens.common.models.CodeMetaData;

/**
 * System Prompt 构建器
 * 
 * 抽取 CLI 端和插件端共用的核心分析规则，统一维护。
 * 
 * 使用方式：
 * <pre>
 * String systemPrompt = SystemPromptBuilder.buildSystemPrompt();
 * </pre>
 */
public class SystemPromptBuilder {

    private SystemPromptBuilder() {
        // 工具类，禁止实例化
    }

    /**
     * 构建完整的 system prompt
     * 
     * @return 完整的 system prompt
     */
    public static String buildSystemPrompt() {
        return "你是Java遗留代码分析专家，专精架构级问题发现。必须严格按JSON格式输出，不要输出任何JSON以外的内容。"
            + "JSON Schema如下：\n"
            + buildSchemaDefinition()
            + "\n要求：\n"
            + buildCoreRules();
    }

    /**
     * 构建 JSON Schema 定义部分
     * 
     * 复用 CodeMetaData.JSON_SCHEMA，确保两端 Schema 一致。
     * 
     * @return JSON Schema 定义字符串
     */
    public static String buildSchemaDefinition() {
        return CodeMetaData.JSON_SCHEMA;
    }

    /**
     * 构建核心分析规则
     * 
     * 从 CLI 端提取的 15 条核心规则，供插件端复用。
     * 已移除 CLI 特有的参数相关规则（--no-validate, --no-cache 等）。
     * 
     * @return 核心规则字符串
     */
    public static String buildCoreRules() {
        return "1. 每条risks和dependencies必须指向具体代码行号（line字段）\n"
            + "2. dependencies必须包含所有依赖注入的字段（标注了[依赖注入]的字段），以及所有第三方和框架类的静态方法调用；同一静态方法在不同业务场景中使用应按用途拆分为多条(如ScheduleUtils.createScheduleJob在init和insertJob中用途不同应分列)，同场景同方法可合并为一条标注首个行号；每条dependency的line指向首次调用行。不要列日志类（Logger/Log），不要列getter/setter，不要列纯值对象类（String/Integer/List等）\n"
            + "3. risks必须基于代码事实，不要猜测，不要写\"需确认\"类模糊描述；每条risk必须包含impact字段说明影响面：什么场景触发、对系统有什么影响、是否可被框架兜住、是否有自动恢复机制(如重启恢复)。severity判断：不可恢复的数据损坏/状态永久不一致=高，未捕获异常导致程序崩溃=高，事务无法补偿的外部系统状态变更=高；被@Transactional兜住会回滚的异常=中(即使抛NPE只要事务回滚就不算高)，逻辑错误导致校验被跳过=中，可恢复的异常=中，代码风格问题=低\n"
            + "4. 必须检查安全风险：路径遍历（文件路径拼接）、SQL注入（表名/列名拼接传入Mapper时若无法确认使用#{}参数化查询应标为风险）、空指针链（链式调用未判空，说明什么输入会触发null）、JSON解析异常，安全类风险不得遗漏\n"
            + "5. 检查异常处理对事务的影响：catch块吞异常会导致Spring事务不回滚，这是事务方法的严重问题；特别关注@Transactional方法中的异常处理\n"
            + "6. 检查跨资源一致性：当一个方法同时操作DB和外部系统（调度器/缓存/消息队列），必须分析两阶段操作的失败场景——DB成功但外部系统失败时状态是否一致，是否有补偿/回滚机制。这类问题必须写入architecture_issues，同时在risks中标注具体代码行\n"
            + "7. architecture_issues不得为空且不得合并为单条！每个维度的问题必须独立列出，至少3条。必须检查以下维度：状态一致性（多资源操作的原子性）、事务边界（@Transactional的粒度和覆盖范围）、并发安全（共享状态的线程安全）、资源管理（连接/流的关闭）、初始化时序（@PostConstruct/静态块的初始化顺序）。每类问题独立一条issue，不要合并不同类别的问题。每个issue必须有category/impact/suggestion三个字段\n"
            + "8. framework_integration不得为空！必须分析本类使用的框架的关键行为和前提条件：框架方法的副作用、框架异常处理机制、框架与DB的事务关系。例如：如果用了Quartz，必须分析JobStore类型（RAMJobStore内存存储vs JobStoreTX/JDBC持久化）对一致性的影响——若是JDBC JobStore则调度器操作和DB操作共享同一数据库，跨资源一致性问题可能不存在；如果是RAMJobStore则是真正的跨资源问题。如果用了Spring事务，要分析@Transactional的传播行为和回滚条件\n"
            + "9. keyMethods必须包含方法上的关键注解（特别是@Transactional、@Async、@Scheduled等影响行为的注解）和可见性\n"
            + "10. 同一类安全风险只列一条risk，在description中列举所有涉及方法，只标首个入口行号\n"
            + "11. class_analysis只写数据流路径，不要重复其他字段内容\n"
            + "12. 只输出JSON，不要markdown代码块包裹\n"
            + "13. 检查Spring AOP自调用问题: 当一个方法内部直接调用同类其他@Transactional方法, 这是通过this调用而非代理, @Transactional不生效, 事务被跳过而非合并. 这类自调用必须标注为风险\n"
            + "14. 架构改进建议必须包含trade-off分析: 每个suggestion需说明解决了什么问题/引入了什么新问题/适用前提条件. 例如先调调度器再改DB的建议需说明: 如果Quartz用JDBC JobStore则调度器操作也在DB事务内, 此建议不适用\n"
            + "15. keyMethods的description字段保持精简，只写关键发现，不要重复purpose已涵盖的内容。优先保证architecture_issues和risks的完整性";
    }
}
