package com.codelens;

public class ColorUtil {
    
    // 检测终端是否支持 ANSI 颜色
    private static Boolean colorEnabled;
    
    public static boolean isColorEnabled() {
        if (colorEnabled == null) {
            // 默认开启，除非 NO_COLOR 环境变量或 --no-color 参数
            String noColor = System.getenv("NO_COLOR");
            colorEnabled = (noColor == null || noColor.isEmpty());
        }
        return colorEnabled;
    }
    
    public static void setColorEnabled(boolean enabled) {
        colorEnabled = enabled;
    }
    
    // ANSI 颜色常量
    public static final String RESET = "\u001B[0m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String MAGENTA = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";
    public static final String BOLD = "\u001B[1m";
    public static final String DIM = "\u001B[2m";
    
    // 语义化着色方法
    public static String business(String text) {
        return isColorEnabled() ? RED + BOLD + text + RESET : text;  // 业务调用：红色加粗
    }
    
    public static String framework(String text) {
        return isColorEnabled() ? DIM + text + RESET : text;  // getter/setter/JDK/工具库：暗淡
    }
    
    public static String heading(String text) {
        return isColorEnabled() ? CYAN + BOLD + text + RESET : text;  // 标题：青色加粗
    }
    
    public static String info(String text) {
        return isColorEnabled() ? GREEN + text + RESET : text;  // 信息：绿色
    }
    
    public static String warning(String text) {
        return isColorEnabled() ? YELLOW + text + RESET : text;  // 警告：黄色
    }
    
    public static String error(String text) {
        return isColorEnabled() ? RED + text + RESET : text;  // 错误：红色
    }
    
    // 风险等级着色
    public static String certain(String text) {
        return isColorEnabled() ? GREEN + BOLD + text + RESET : text;  // CERTAIN：绿色加粗
    }
    
    public static String high(String text) {
        return isColorEnabled() ? YELLOW + BOLD + text + RESET : text;  // HIGH：黄色加粗
    }
    
    public static String medium(String text) {
        return isColorEnabled() ? YELLOW + text + RESET : text;  // MEDIUM：黄色
    }
    
    public static String low(String text) {
        return isColorEnabled() ? RED + BOLD + text + RESET : text;  // LOW：红色加粗
    }
}
