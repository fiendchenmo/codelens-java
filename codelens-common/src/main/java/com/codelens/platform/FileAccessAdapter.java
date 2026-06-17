// SYNC_VERSION: 2026-06-17-v1
// IMPACT: LOGIC_CHANGE
package com.codelens.platform;

import java.nio.file.Path;
import java.util.List;

/**
 * 文件访问抽象层 — 替代 IDEA VirtualFile
 * 插件端实现用 VirtualFile，CLI 端实现用 Java NIO
 */
public interface FileAccessAdapter {

    /**
     * 获取项目根目录
     */
    Path getProjectRoot();

    /**
     * 扫描项目中的 Java 文件
     *
     * @param pattern 文件匹配模式（如 {@code **&#47;*.java}）
     * @return 匹配的文件路径列表
     */
    List<Path> findFiles(String pattern);

    /**
     * 读取文件内容
     *
     * @param file 文件路径
     * @return 文件内容字符串
     */
    String readFileContent(Path file);

    /**
     * 检查文件是否存在
     */
    boolean exists(Path file);

    /**
     * 获取文件最后修改时间（毫秒时间戳）
     */
    long getLastModified(Path file);

    /**
     * 将 VirtualFile（或其他平台对象）转换为 Path
     * 插件端实现：VirtualFile → Path
     * CLI 端实现：直接返回 Path
     */
    Path toPath(Object platformFile);
}
