package com.codelens.common.benchmark;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试样本加载器。
 * <p>
 * 从 classpath 或文件系统加载测试样本。
 * 样本格式：{name}.java + {name}.expected.json
 */
public class SampleLoader {

    private final Path baseDir;

    /**
     * @param baseDir 样本目录
     */
    public SampleLoader(Path baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * 加载单个样本。
     *
     * @param name 样本名称（不含扩展名）
     * @return Sample 对象
     * @throws IOException 如果文件不存在或读取失败
     */
    public Sample load(String name) throws IOException {
        Path javaFile = baseDir.resolve(name + ".java");
        Path expectedFile = baseDir.resolve(name + ".expected.json");

        if (!Files.exists(javaFile)) {
            throw new IOException("样本文件不存在: " + javaFile);
        }

        String sourceCode = new String(Files.readAllBytes(javaFile), StandardCharsets.UTF_8);
        String expectedJson = Files.exists(expectedFile)
                ? new String(Files.readAllBytes(expectedFile), StandardCharsets.UTF_8)
                : null;

        return new Sample(name, sourceCode, expectedJson);
    }

    /**
     * 加载目录下所有样本。
     */
    public List<Sample> loadAll() throws IOException {
        List<Sample> samples = new ArrayList<>();
        if (!Files.exists(baseDir) || !Files.isDirectory(baseDir)) {
            return samples;
        }

        File[] files = baseDir.toFile().listFiles((dir, name) -> name.endsWith(".java"));
        if (files == null) return samples;

        for (File f : files) {
            String name = f.getName();
            name = name.substring(0, name.length() - 5); // 去掉 .java
            samples.add(load(name));
        }

        return samples;
    }

    /**
     * 测试样本数据类。
     */
    public static class Sample {
        private final String name;
        private final String sourceCode;
        private final String expectedJson;

        public Sample(String name, String sourceCode, String expectedJson) {
            this.name = name;
            this.sourceCode = sourceCode;
            this.expectedJson = expectedJson;
        }

        public String getName() { return name; }
        public String getSourceCode() { return sourceCode; }
        public String getExpectedJson() { return expectedJson; }
    }
}
