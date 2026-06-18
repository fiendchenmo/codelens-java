package com.codelens.platform;

import java.nio.file.Path;
import java.util.List;

/**
 * 代码内嵌提示抽象层 — 在编辑器中直接显示分析结果
 * 
 * <p>插件端实现用 IDEA InlayHints API，CLI 端实现可输出到终端或忽略
 */
public interface InlayHintAdapter {

    /**
     * 获取指定文件的内嵌提示列表
     *
     * @param filePath 文件路径
     * @return 提示列表，无提示时返回空列表（不返回 null）
     */
    List<InlayHint> getHints(Path filePath);

    /**
     * 内嵌提示数据模型
     */
    class InlayHint {
        /** 行号（0-based） */
        private final int line;
        
        /** 列偏移（0-based） */
        private final int column;
        
        /** 提示类型 */
        private final HintType type;
        
        /** 显示文本 */
        private final String text;
        
        /** hover 提示（可选，null 表示无 tooltip） */
        private final String tooltip;
        
        /** 严重程度（可选，null 表示无级别） */
        private final Severity severity;

        public InlayHint(int line, int column, HintType type, String text, String tooltip, Severity severity) {
            this.line = line;
            this.column = column;
            this.type = type;
            this.text = text;
            this.tooltip = tooltip;
            this.severity = severity;
        }

        public int getLine() {
            return line;
        }

        public int getColumn() {
            return column;
        }

        public HintType getType() {
            return type;
        }

        public String getText() {
            return text;
        }

        public String getTooltip() {
            return tooltip;
        }

        public Severity getSeverity() {
            return severity;
        }
    }

    /**
     * 提示类型枚举
     */
    enum HintType {
        /** 架构层标签（如 [SERVICE] [DAO]） */
        ARCHITECTURE_LAYER,
        
        /** 风险标注（如 ⚠ HIGH） */
        RISK_WARNING,
        
        /** 调用复杂度（如 → 5 callers） */
        CALL_COMPLEXITY,
        
        /** 依赖方向（如 ↑ 跨层调用） */
        DEPENDENCY_DIR
    }

    /**
     * 严重程度枚举
     */
    enum Severity {
        /** 高风险/严重问题 */
        HIGH,
        
        /** 中风险/一般问题 */
        MEDIUM,
        
        /** 低风险/信息提示 */
        LOW
    }
}
