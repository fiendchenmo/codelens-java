package com.codelens.agent.context;

import java.util.List;

/**
 * L0 项目级上下文。Agent 初始化时构建，注入 System Prompt。
 * <p>
 * Claude Code 模式：环境上下文（项目名、技术栈、模块列表等），
 * 让 LLM 了解"在什么项目中工作"。
 * </p>
 */
public class ProjectContext {

    private String projectName;
    private String techStack;
    private List<String> entryClasses;
    private List<String> modules;

    public ProjectContext() {}

    public ProjectContext(String projectName, String techStack,
                          List<String> entryClasses, List<String> modules) {
        this.projectName = projectName;
        this.techStack = techStack;
        this.entryClasses = entryClasses;
        this.modules = modules;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getTechStack() {
        return techStack;
    }

    public void setTechStack(String techStack) {
        this.techStack = techStack;
    }

    public List<String> getEntryClasses() {
        return entryClasses;
    }

    public void setEntryClasses(List<String> entryClasses) {
        this.entryClasses = entryClasses;
    }

    public List<String> getModules() {
        return modules;
    }

    public void setModules(List<String> modules) {
        this.modules = modules;
    }

    /** 格式化为可嵌入 System Prompt 的文本 */
    public String toPromptText() {
        StringBuilder sb = new StringBuilder();
        if (projectName != null && !projectName.isEmpty()) {
            sb.append("项目名称: ").append(projectName);
        }
        if (techStack != null && !techStack.isEmpty()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("技术栈: ").append(techStack);
        }
        if (entryClasses != null && !entryClasses.isEmpty()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("入口类: ").append(String.join(", ", entryClasses));
        }
        if (modules != null && !modules.isEmpty()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("模块: ").append(String.join(", ", modules));
        }
        return sb.toString();
    }
}
