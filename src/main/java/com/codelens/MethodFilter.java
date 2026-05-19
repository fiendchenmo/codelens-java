package com.codelens;

/**
 * 方法过滤器 - 判断方法是否为简单调用或基础设施调用
 * 
 * 用于在代码分析时过滤掉不需要关注的简单方法调用，
 * 如 getter/setter、toString、hashCode、equals 等。
 */
public class MethodFilter {

    private MethodFilter() {
        // 工具类，禁止实例化
    }

    /**
     * 判断是否为简单调用（getter/setter/toString/hashCode/equals等）
     * 
     * @param methodName 方法名
     * @return true 如果是简单调用
     */
    public static boolean isTrivialCall(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3
                && Character.isUpperCase(methodName.charAt(3))) return true;
        if (methodName.startsWith("set") && methodName.length() > 3
                && Character.isUpperCase(methodName.charAt(3))) return true;
        if (methodName.startsWith("is") && methodName.length() > 2
                && Character.isUpperCase(methodName.charAt(2))) return true;
        if (methodName.equals("toString")) return true;
        if (methodName.equals("hashCode")) return true;
        if (methodName.equals("equals")) return true;
        if (methodName.equals("getClass")) return true;
        if (methodName.equals("valueOf")) return true;
        if (methodName.equals("add") || methodName.equals("size")
                || methodName.equals("contains") || methodName.equals("remove")
                || methodName.equals("iterator") || methodName.equals("toArray")) return true;
        if (methodName.equals("startPage")) return true;
        if (methodName.equals("getDataTable")) return true;
        if (methodName.equals("toAjax")) return true;
        if (methodName.equals("error")) return true;
        if (methodName.equals("success")) return true;
        
        return false;
    }

    /**
     * 判断是否为基础设施调用（Logger/Collections/Arrays等）
     * 
     * @param methodName 方法名
     * @param caller 调用者（可选，可为null）
     * @return true 如果是基础设施调用
     */
    public static boolean isInfrastructureCall(String methodName, String caller) {
        // getter/setter/is
        if (methodName.startsWith("get") && methodName.length() > 3
                && Character.isUpperCase(methodName.charAt(3))) return true;
        if (methodName.startsWith("set") && methodName.length() > 3
                && Character.isUpperCase(methodName.charAt(3))) return true;
        if (methodName.startsWith("is") && methodName.length() > 2
                && Character.isUpperCase(methodName.charAt(2))) return true;
        // JDK/工具库包名
        if (caller != null) {
            if (caller.startsWith("System.") || caller.startsWith("Collections.")
                    || caller.startsWith("Arrays.") || caller.startsWith("Objects.")) return true;
            if (caller.startsWith("Logger.") || caller.startsWith("log.")
                    || caller.startsWith("logger.")) return true;
        }
        // 常见集合/工具方法
        if (methodName.equals("toString") || methodName.equals("hashCode")
                || methodName.equals("equals") || methodName.equals("getClass")
                || methodName.equals("valueOf") || methodName.equals("add")
                || methodName.equals("size") || methodName.equals("contains")
                || methodName.equals("remove") || methodName.equals("iterator")
                || methodName.equals("toArray") || methodName.equals("put")
                || methodName.equals("get") || methodName.equals("stream")
                || methodName.equals("collect") || methodName.equals("forEach")) return true;
        return false;
    }

    /**
     * 判断是否为 RuoYi 框架特有的表名参数方法
     * 
     * 这类方法通常接收表名/列名作为参数，传给 Mapper，
     * 需要检查 SQL 是否使用 ${} 拼接（可能有SQL注入风险）
     * 
     * @param methodName 方法名
     * @return true 如果是表名参数方法
     */
    public static boolean isTableNameParamMethod(String methodName) {
        return methodName.contains("ByName") || methodName.contains("ByNames")
            || methodName.contains("ByNameList") || methodName.contains("ByTableName")
            || methodName.contains("ByColumn") || methodName.contains("ByColumns");
    }
}
