package com.codelens.agent.context;

/**
 * Agent 三级上下文提供者。
 * <p>
 * Claude Code 模式：分层上下文注入。
 * </p>
 * <table>
 *   <tr><th>层级</th><th>名称</th><th>内容</th><th>注入时机</th><th>更新频率</th></tr>
 *   <tr><td>L0</td><td>项目级</td><td>模块列表、技术栈、入口类</td><td>Agent 创建时注入 System Prompt</td><td>项目结构变更时</td></tr>
 *   <tr><td>L1</td><td>当前文件</td><td>文件路径、类名、光标所在方法 + 面板摘要</td><td>每轮对话注入 UserMessage 前缀</td><td>EditorListener 实时更新</td></tr>
 *   <tr><td>L2</td><td>对话级</td><td>本轮问答涉及的类/方法</td><td>LLM 自行通过 Tool 调用构建</td><td>每轮对话自动积累</td></tr>
 * </table>
 */
public class AnalysisContextProvider {

    // ── L0: 项目级（低频更新） ──

    private volatile ProjectContext projectContext;

    /** 初始化 L0 项目上下文 */
    public void initProjectContext(ProjectContext context) {
        this.projectContext = context;
    }

    /** 获取 L0 项目摘要文本 */
    public String getProjectSummary() {
        if (projectContext == null) {
            return "";
        }
        return projectContext.toPromptText();
    }

    // ── L1: 当前文件（EditorListener 实时更新） ──

    private volatile String currentFilePath;
    private volatile String currentClassName;
    private volatile String currentMethodName;
    private volatile String v3PanelSummary;
    private volatile String packagePanelSummary;
    private volatile String diffPanelSummary;

    /**
     * 更新当前文件上下文（编辑器切换时调用）。
     */
    public void updateFileContext(String filePath, String className, String methodName) {
        this.currentFilePath = filePath;
        this.currentClassName = className;
        this.currentMethodName = methodName;
    }

    /**
     * 更新面板摘要（面板数据变更时调用）。
     */
    public void updatePanelSummaries(String v3Summary, String packageSummary, String diffSummary) {
        this.v3PanelSummary = v3Summary;
        this.packagePanelSummary = packageSummary;
        this.diffPanelSummary = diffSummary;
    }

    /**
     * 获取 L1 文件上下文摘要，注入 UserMessage 前缀。
     * 包含当前文件位置 + 面板摘要（如果可用）。
     */
    public String getFileContextSummary() {
        StringBuilder sb = new StringBuilder();

        // 文件位置
        if (currentFilePath != null && !currentFilePath.isEmpty()) {
            sb.append("[当前文件] ").append(currentFilePath);
            if (currentClassName != null && !currentClassName.isEmpty()) {
                sb.append(" (").append(currentClassName);
                if (currentMethodName != null && !currentMethodName.isEmpty()) {
                    sb.append(".").append(currentMethodName);
                }
                sb.append(")");
            }
            sb.append("\n");
        }

        // 面板摘要
        if (v3PanelSummary != null && !v3PanelSummary.isEmpty()) {
            sb.append("[V3 面板摘要] ").append(v3PanelSummary).append("\n");
        }
        if (packagePanelSummary != null && !packagePanelSummary.isEmpty()) {
            sb.append("[包分析面板摘要] ").append(packagePanelSummary).append("\n");
        }
        if (diffPanelSummary != null && !diffPanelSummary.isEmpty()) {
            sb.append("[Diff 面板摘要] ").append(diffPanelSummary).append("\n");
        }

        return sb.toString().trim();
    }

    // ── L2: 对话级 ──
    // 由 Agent.history 自动管理，无需系统注入
}
