// SYNC_SOURCE: codelens-java/src/main/java/com/codelens/ColorUtil.java
// SYNC_VERSION: 2026-05-16-v1
// 维护方：喵呜（CLI端），prompt/校验器相关由喵呜拍板
// 同步说明：零 IntelliJ SDK 依赖，纯文本处理

package com.codelens;

/**
 * 终端颜色工具类
 * 提供 ANSI 转义序列，用于 CLI 输出着色
 */
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
    public static final String RESET = "[0m";
    public static final String RED = "[31m";
    public static final String GREEN = "[32m";
    public static final String YELLOW = "[33m";
    public static final String BLUE = "[34m";
    public static final String MAGENTA = "[35m";
    public static final String CYAN = "[36m";
    public static final String WHITE = "[37m";
    public static final String BOLD = "[1m";
    public static final String DIM = "[2m";

    // 语义化着色方法
    public static String business(String text) {
        return isColorEnabled() ? RED + BOLD + text + RESET : text;
    }

    public static String framework(String text) {
        return isColorEnabled() ? DIM + text + RESET : text;
    }

    public static String heading(String text) {
        return isColorEnabled() ? CYAN + BOLD + text + RESET : text;
    }

    public static String info(String text) {
        return isColorEnabled() ? GREEN + text + RESET : text;
    }

    public static String warning(String text) {
        return isColorEnabled() ? YELLOW + text + RESET : text;
    }

    public static String error(String text) {
        return isColorEnabled() ? RED + text + RESET : text;
    }

    public static String certain(String text) {
        return isColorEnabled() ? GREEN + BOLD + text + RESET : text;
    }

    public static String high(String text) {
        return isColorEnabled() ? YELLOW + BOLD + text + RESET : text;
    }

    public static String medium(String text) {
        return isColorEnabled() ? YELLOW + text + RESET : text;
    }

    public static String low(String text) {
        return isColorEnabled() ? RED + BOLD + text + RESET : text;
    }
}
