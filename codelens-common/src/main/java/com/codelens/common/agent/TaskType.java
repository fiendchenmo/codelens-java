package com.codelens.common.agent;

/**
 * 多 Agent 流水线任务类型。
 * <p>
 * 每个枚举值关联对应的 Prompt 类和 Validator 类，Phase 1 暂设为 null，
 * 后续 Phase 逐步填充。
 */
public enum TaskType {

    STRUCTURE_EXTRACTION(null, null),
    SUMMARY(SummaryPrompt.class, SummaryValidator.class),
    METHOD_ANALYSIS(MethodAnalysisPrompt.class, MethodAnalysisValidator.class),
    CROSS_FILE_INFERENCE(null, null);

    private final Class<?> promptClass;
    private final Class<?> validatorClass;

    TaskType(Class<?> promptClass, Class<?> validatorClass) {
        this.promptClass = promptClass;
        this.validatorClass = validatorClass;
    }

    public Class<?> getPromptClass() {
        return promptClass;
    }

    public Class<?> getValidatorClass() {
        return validatorClass;
    }
}
