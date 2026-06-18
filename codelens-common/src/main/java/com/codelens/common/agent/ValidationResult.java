package com.codelens.common.agent;

/**
 * 校验结果数据类。
 * <p>
 * 不可变对象，通过静态工厂方法 ok() 和 fail() 创建。
 */
public class ValidationResult {

    private final boolean valid;
    private final String errorMessage;
    private final String fieldName;
    private final String correctedOutput;

    private ValidationResult(boolean valid, String fieldName, String errorMessage, String correctedOutput) {
        this.valid = valid;
        this.errorMessage = errorMessage;
        this.fieldName = fieldName;
        this.correctedOutput = correctedOutput;
    }

    public boolean isValid() {
        return valid;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getFieldName() {
        return fieldName;
    }

    /**
     * 获取修正后的输出 JSON。
     * <p>
     * Validator 在校验过程中可能会对输出对象进行修正（如填充缺失字段、覆盖统计数据等），
     * 此字段返回修正后重新序列化的 JSON。调用方应优先使用此值而非原始 LLM 输出。
     *
     * @return 修正后的 JSON 字符串，如果没有修正则返回 null
     */
    public String getCorrectedOutput() {
        return correctedOutput;
    }

    public static ValidationResult ok() {
        return new ValidationResult(true, null, null, null);
    }

    /**
     * 创建成功的校验结果，携带修正后的输出。
     *
     * @param correctedOutput 修正后重新序列化的 JSON
     * @return 校验结果
     */
    public static ValidationResult okWithCorrection(String correctedOutput) {
        return new ValidationResult(true, null, null, correctedOutput);
    }

    public static ValidationResult fail(String fieldName, String errorMessage) {
        return new ValidationResult(false, fieldName, errorMessage, null);
    }
}
