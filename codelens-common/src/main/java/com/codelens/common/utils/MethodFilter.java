package com.codelens.common.utils;

/**
 * 方法过滤器 - 判断方法是否为简单调用或基础设施调用
 */
public class MethodFilter {

    private MethodFilter() {}

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

    public static boolean isInfrastructureCall(String methodName, String caller) {
        if (methodName.startsWith("get") && methodName.length() > 3
                && Character.isUpperCase(methodName.charAt(3))) return true;
        if (methodName.startsWith("set") && methodName.length() > 3
                && Character.isUpperCase(methodName.charAt(3))) return true;
        if (methodName.startsWith("is") && methodName.length() > 2
                && Character.isUpperCase(methodName.charAt(2))) return true;
        if (caller != null) {
            if (caller.startsWith("System.") || caller.startsWith("Collections.")
                    || caller.startsWith("Arrays.") || caller.startsWith("Objects.")) return true;
            if (caller.startsWith("Logger.") || caller.startsWith("log.")
                    || caller.startsWith("logger.")) return true;
        }
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

    public static boolean isTableNameParamMethod(String methodName) {
        return methodName.contains("ByName") || methodName.contains("ByNames")
            || methodName.contains("ByNameList") || methodName.contains("ByTableName")
            || methodName.contains("ByColumn") || methodName.contains("ByColumns");
    }
}
