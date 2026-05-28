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

    private ValidationResult(boolean valid, String fieldName, String errorMessage) {
        this.valid = valid;
        this.errorMessage = errorMessage;
        this.fieldName = fieldName;
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

    public static ValidationResult ok() {
        return new ValidationResult(true, null, null);
    }

    public static ValidationResult fail(String fieldName, String errorMessage) {
        return new ValidationResult(false, fieldName, errorMessage);
    }
}
