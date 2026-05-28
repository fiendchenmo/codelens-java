package com.codelens.common.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SampleLoaderTest {

    @Test
    public void testLoad_Success(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("TestService.java");
        Path expectedFile = tempDir.resolve("TestService.expected.json");
        Files.write(javaFile, "public class TestService {}".getBytes(StandardCharsets.UTF_8));
        Files.write(expectedFile, "{\"result\":\"ok\"}".getBytes(StandardCharsets.UTF_8));

        SampleLoader loader = new SampleLoader(tempDir);
        SampleLoader.Sample sample = loader.load("TestService");

        assertEquals("TestService", sample.getName());
        assertEquals("public class TestService {}", sample.getSourceCode());
        assertEquals("{\"result\":\"ok\"}", sample.getExpectedJson());
    }

    @Test
    public void testLoad_MissingJavaFile() throws IOException {
        SampleLoader loader = new SampleLoader(Files.createTempDirectory("test"));
        IOException exception = assertThrows(IOException.class, () -> loader.load("NonExistent"));
        assertTrue(exception.getMessage().contains("不存在"));
    }

    @Test
    public void testLoadAll_EmptyDirectory(@TempDir Path tempDir) throws IOException {
        SampleLoader loader = new SampleLoader(tempDir);
        List<SampleLoader.Sample> samples = loader.loadAll();
        assertTrue(samples.isEmpty());
    }
}
