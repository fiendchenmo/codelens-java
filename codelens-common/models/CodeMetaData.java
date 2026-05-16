// SYNC_VERSION: 2026-05-16-v2
// 维护方：喵呜（CLI端），prompt/校验器相关由喵呜拍板
// 同步说明：两端共用 JSON Schema，修改需双方确认

package com.codelens.common.models;

/**
 * 代码元数据结构
 * LLM 返回的 JSON 结构定义
 */
public class CodeMetaData {

    /**
     * 依赖项结构
     * 
     * @param name        依赖名称
     * @param type        依赖类型：import|field|method_call
     * @param line        所在行号
     * @param description 描述（合并 reason → description）
     */
    public static class Dependency {
        public String name;        // 依赖名称
        public String type;       // 依赖类型：import|field|method_call
        public String line;       // 所在行号
        public String description; // 描述
    }

    /**
     * 风险项结构
     * 
     * @param type        风险类型：SECURITY|PERFORMANCE|MAINTAINABILITY
     * @param severity    严重程度：HIGH|MEDIUM|LOW
     * @param description 风险描述
     * @param line        所在行号
     * @param suggestion  修复建议
     */
    public static class Risk {
        public String type;       // 风险类型：SECURITY|PERFORMANCE|MAINTAINABILITY
        public String severity;   // 严重程度：HIGH|MEDIUM|LOW
        public String description; // 风险描述
        public String line;       // 所在行号
        public String suggestion;  // 修复建议
    }

    /**
     * 关键方法结构（camelCase，Java规范）
     * 
     * @param name        方法名
     * @param line        所在行号
     * @param signature   方法签名
     * @param visibility  可见性：public|private|protected
     * @param complexity  复杂度：LOW|MEDIUM|HIGH
     * @param calls       调用次数
     * @param description 方法功能描述（合并 notes → description）
     */
    public static class KeyMethod {
        public String name;       // 方法名
        public String line;       // 所在行号
        public String signature;   // 方法签名
        public String visibility; // 可见性：public|private|protected
        public String complexity;  // 复杂度：LOW|MEDIUM|HIGH
        public String calls;       // 调用次数
        public String description; // 方法功能描述
    }

    // ==================== JSON Schema ====================
    
    /**
     * LLM 输出 JSON Schema v2（用于 prompt 中的示例）
     * 
     * {
     *   "summary": "类功能概述",
     *   "confidence": "CERTAIN",
     *   "dependencies": [
     *     {"name": "UserMapper", "type": "field", "line": "15", "description": "用户数据访问"}
     *   ],
     *   "risks": [
     *     {"type": "SECURITY", "severity": "HIGH", "description": "SQL注入风险", "line": "28", "suggestion": "使用参数化查询"}
     *   ],
     *   "keyMethods": [
     *     {"name": "createUser", "line": "30", "signature": "public User createUser(String name)", "visibility": "public", "complexity": "MEDIUM", "calls": "3", "description": "创建用户"}
     *   ]
     * }
     */
    public static final String JSON_SCHEMA = """
    {
      "summary": "string (类功能概述，必填)",
      "confidence": "string (CERTAIN|HIGH|MEDIUM|LOW，可选)",
      "dependencies": [
        {
          "name": "string (依赖类/变量名，必填)",
          "type": "string (import|field|method_call)",
          "line": "string (行号，数字转字符串)",
          "description": "string (1-2句描述)"
        }
      ],
      "risks": [
        {
          "type": "string (SECURITY|PERFORMANCE|MAINTAINABILITY)",
          "severity": "string (HIGH|MEDIUM|LOW)",
          "description": "string (风险描述)",
          "line": "string (行号)",
          "suggestion": "string (修复建议)"
        }
      ],
      "keyMethods": [
        {
          "name": "string (方法名，必填)",
          "line": "string (行号)",
          "signature": "string (方法签名，如 public User createUser(String name))",
          "visibility": "string (public|private|protected)",
          "complexity": "string (LOW|MEDIUM|HIGH)",
          "calls": "string (调用次数，数字转字符串)",
          "description": "string (方法功能描述)"
        }
      ]
    }
    """;

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
        public static final String CONF_CERTAIN = "[CERTAIN]";                  // 置信度：确定
        public static final String CONF_HIGH = "[HIGH]";                        // 置信度：高
        public static final String CONF_MEDIUM = "[MEDIUM]";                    // 置信度：中
        public static final String CONF_LOW = "[LOW]";                          // 置信度：低
        
        // 特殊标记
        public static final String HALLUCINATION = "[HALLUCINATION]";            // 疑似幻觉
        public static final String NEED_REVIEW = "[NEED_REVIEW]";               // 需要人工审核
    }
}
