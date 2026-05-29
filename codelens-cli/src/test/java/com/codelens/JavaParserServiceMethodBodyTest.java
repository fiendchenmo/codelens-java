package com.codelens;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JavaParserServiceMethodBodyTest {

    @Test
    public void testEndLinePopulated(@TempDir Path tempDir) throws Exception {
        Path javaFile = tempDir.resolve("TestService.java");
        Files.write(javaFile, (
                "package com.test;\n" +
                "public class TestService {\n" +
                "    public void methodOne() {\n" +
                "        System.out.println(\"hello\");\n" +
                "    }\n" +
                "    public String methodTwo(String input) {\n" +
                "        return input.toUpperCase();\n" +
                "    }\n" +
                "}\n"
        ).getBytes());

        List<JavaParserService.ClassInfo> classInfos = JavaParserService.parseFile(javaFile.toFile());
        assertEquals(1, classInfos.size());
        assertEquals(2, classInfos.get(0).methods.size());

        JavaParserService.MethodInfo m1 = classInfos.get(0).methods.get(0);
        assertEquals("methodOne", m1.name);
        assertEquals(3, m1.line);  // start line
        assertTrue(m1.endLine >= m1.line, "endLine 应 >= startLine");

        JavaParserService.MethodInfo m2 = classInfos.get(0).methods.get(1);
        assertEquals("methodTwo", m2.name);
        assertEquals(6, m2.line);
        assertTrue(m2.endLine >= m2.line, "endLine 应 >= startLine");
    }

    @Test
    public void testExtractMethodBody(@TempDir Path tempDir) throws Exception {
        Path javaFile = tempDir.resolve("Calculator.java");
        Files.write(javaFile, (
                "public class Calculator {\n" +
                "    public int add(int a, int b) {\n" +
                "        return a + b;\n" +
                "    }\n" +
                "}\n"
        ).getBytes());

        List<JavaParserService.ClassInfo> classInfos = JavaParserService.parseFile(javaFile.toFile());
        assertEquals(1, classInfos.get(0).methods.size());

        JavaParserService.MethodInfo method = classInfos.get(0).methods.get(0);
        String body = JavaParserService.extractMethodBody(javaFile.toFile(), method);

        assertNotNull(body);
        assertTrue(body.contains("add"), "方法体应包含方法名");
        assertTrue(body.contains("return a + b"), "方法体应包含方法逻辑");
        assertTrue(body.contains("int b"), "方法体应包含参数");
    }
}
