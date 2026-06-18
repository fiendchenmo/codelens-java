// SYNC_VERSION: 2026-06-18-v1
// IMPACT: LOGIC_CHANGE
package com.codelens.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * CLI 端文件访问实现 — 基于 Java NIO
 * 适用于无 IDE 环境的纯命令行分析场景
 */
public class CliFileAccessAdapter implements FileAccessAdapter {

    private final Path projectRoot;

    public CliFileAccessAdapter(Path projectRoot) {
        this.projectRoot = projectRoot;
    }

    public CliFileAccessAdapter() {
        this.projectRoot = Paths.get("").toAbsolutePath();
    }

    @Override
    public Path getProjectRoot() {
        return projectRoot;
    }

    @Override
    public List<Path> findFiles(String pattern) {
        if (!Files.exists(projectRoot)) {
            return Collections.emptyList();
        }
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        try (Stream<Path> walk = Files.walk(projectRoot)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(matcher::matches)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public String readFileContent(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    @Override
    public boolean exists(Path file) {
        return Files.exists(file);
    }

    @Override
    public long getLastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    @Override
    public Path toPath(Object platformFile) {
        if (platformFile instanceof Path) {
            return (Path) platformFile;
        }
        if (platformFile instanceof String) {
            return Paths.get((String) platformFile);
        }
        throw new IllegalArgumentException("Unsupported file type: " + platformFile.getClass());
    }
}
