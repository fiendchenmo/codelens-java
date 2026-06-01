package com.codelens;

import com.codelens.common.callindex.CallRecord;
import com.codelens.common.callindex.SQLiteCallIndex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CallIndexBuilder 单元测试。
 * <p>
 * 覆盖 TC-CIB-01 ~ TC-CIB-08 测试用例。
 * 使用临时目录创建真实的 Java 源文件进行测试。
 * </p>
 */
public class CallIndexBuilderTest {

    private Path tempDir;
    private SQLiteCallIndex index;

    @AfterEach
    void tearDown() {
        if (index != null) {
            try { index.close(); } catch (Exception ignored) { }
        }
        if (tempDir != null) {
            deleteDir(tempDir.toFile());
        }
    }

    // ==================== TC-CIB-01: 字段调用推导 ====================

    @Test
    void testFieldInjectionCallResolution() throws Exception {
        // Setup: A.java with AgentRunner runner; and method calling runner.run()
        tempDir = createTempProject();
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        // AgentRunner.java (dependency)
        writeFile(srcDir.resolve("AgentRunner.java"),
                "package com.example;\n" +
                "public class AgentRunner {\n" +
                "    public void run() {}\n" +
                "    public void runAggregate() {}\n" +
                "}\n");

        // A.java (uses AgentRunner via field injection)
        writeFile(srcDir.resolve("A.java"),
                "package com.example;\n" +
                "public class A {\n" +
                "    private AgentRunner runner;\n" +
                "    void execute() {\n" +
                "        runner.run();\n" +
                "    }\n" +
                "}\n");

        int count = CallIndexBuilder.build(tempDir);
        assertEquals(1, count, "Should find 1 non-trivial call");

        // Verify the record
        index = new SQLiteCallIndex(tempDir.resolve(".codelens/callindex.db").toString());
        List<CallRecord> records = index.queryByCaller("com.example.A", "execute");
        assertEquals(1, records.size());
        CallRecord record = records.get(0);
        assertEquals("com.example.A", record.getCallerClass());
        assertEquals("execute", record.getCallerMethod());
        assertEquals("com.example.AgentRunner", record.getCalleeClass());
        assertEquals("run", record.getCalleeMethod());
        assertEquals("DIRECT", record.getCallType());
    }

    // ==================== TC-CIB-02: 静态方法调用 ====================

    @Test
    void testStaticMethodCall() throws Exception {
        tempDir = createTempProject();
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        writeFile(srcDir.resolve("Detector.java"),
                "package com.example;\n" +
                "public class Detector {\n" +
                "    public static void detect() {}\n" +
                "}\n");

        writeFile(srcDir.resolve("B.java"),
                "package com.example;\n" +
                "public class B {\n" +
                "    void process() {\n" +
                "        Detector.detect();\n" +
                "    }\n" +
                "}\n");

        int count = CallIndexBuilder.build(tempDir);
        assertEquals(1, count);

        index = new SQLiteCallIndex(tempDir.resolve(".codelens/callindex.db").toString());
        List<CallRecord> records = index.queryByCaller("com.example.B", "process");
        assertEquals(1, records.size());
        assertEquals("com.example.Detector", records.get(0).getCalleeClass());
        assertEquals("detect", records.get(0).getCalleeMethod());
    }

    // ==================== TC-CIB-03: this 调用 ====================

    @Test
    void testThisCall() throws Exception {
        tempDir = createTempProject();
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        writeFile(srcDir.resolve("SelfCaller.java"),
                "package com.example;\n" +
                "public class SelfCaller {\n" +
                "    void process() {\n" +
                "        this.helper();\n" +
                "    }\n" +
                "    void helper() {}\n" +
                "}\n");

        int count = CallIndexBuilder.build(tempDir);
        assertEquals(1, count);

        index = new SQLiteCallIndex(tempDir.resolve(".codelens/callindex.db").toString());
        List<CallRecord> records = index.queryByCaller("com.example.SelfCaller", "process");
        assertEquals(1, records.size());
        // this.helper() → calleeClass = currentClass
        assertEquals("com.example.SelfCaller", records.get(0).getCalleeClass());
        assertEquals("helper", records.get(0).getCalleeMethod());
    }

    // ==================== TC-CIB-04: 无 scope 调用 ====================

    @Test
    void testNoScopeCall() throws Exception {
        tempDir = createTempProject();
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        writeFile(srcDir.resolve("NoScopeCaller.java"),
                "package com.example;\n" +
                "public class NoScopeCaller {\n" +
                "    void process() {\n" +
                "        someMethod();\n" +
                "    }\n" +
                "    void someMethod() {}\n" +
                "}\n");

        int count = CallIndexBuilder.build(tempDir);
        assertEquals(1, count);

        index = new SQLiteCallIndex(tempDir.resolve(".codelens/callindex.db").toString());
        List<CallRecord> records = index.queryByCaller("com.example.NoScopeCaller", "process");
        assertEquals(1, records.size());
        // No scope → calleeClass = "UNKNOWN"
        assertEquals("UNKNOWN", records.get(0).getCalleeClass());
        assertEquals("someMethod", records.get(0).getCalleeMethod());
    }

    // ==================== TC-CIB-05: buildIfEmpty 跳过 ====================

    @Test
    void testBuildIfEmptySkipsExisting() throws Exception {
        tempDir = createTempProject();
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        // First build: create a file with a real call so records are written
        writeFile(srcDir.resolve("ServiceA.java"),
                "package com.example;\n" +
                "public class ServiceA {\n" +
                "    void process() {\n" +
                "        helper();\n" +
                "    }\n" +
                "    void helper() {}\n" +
                "}\n");

        int count1 = CallIndexBuilder.build(tempDir);
        assertEquals(1, count1, "First build should find 1 call");

        // Now add a second file
        writeFile(srcDir.resolve("ServiceB.java"),
                "package com.example;\n" +
                "public class ServiceB {\n" +
                "    void execute() {\n" +
                "        helper();\n" +
                "    }\n" +
                "    void helper() {}\n" +
                "}\n");

        // buildIfEmpty should skip because DB already has records
        int count2 = CallIndexBuilder.buildIfEmpty(tempDir);
        // Should return existing count (1), not the new one (which would be 2)
        assertEquals(1, count2);
    }

    // ==================== TC-CIB-06: 多文件跨包调用 ====================

    @Test
    void testMultiFileCrossPackageCall() throws Exception {
        tempDir = createTempProject();
        Path srcMain = tempDir.resolve("src/main/java");

        // service package
        Path serviceDir = srcMain.resolve("com/example/service");
        Files.createDirectories(serviceDir);
        writeFile(serviceDir.resolve("OrderService.java"),
                "package com.example.service;\n" +
                "public class OrderService {\n" +
                "    public void processOrder() {}\n" +
                "}\n");

        // controller package
        Path controllerDir = srcMain.resolve("com/example/controller");
        Files.createDirectories(controllerDir);
        writeFile(controllerDir.resolve("OrderController.java"),
                "package com.example.controller;\n" +
                "import com.example.service.OrderService;\n" +
                "public class OrderController {\n" +
                "    private OrderService orderService;\n" +
                "    void submit() {\n" +
                "        orderService.processOrder();\n" +
                "    }\n" +
                "}\n");

        int count = CallIndexBuilder.build(tempDir);
        assertEquals(1, count);

        index = new SQLiteCallIndex(tempDir.resolve(".codelens/callindex.db").toString());
        List<CallRecord> records = index.queryByCaller("com.example.controller.OrderController", "submit");
        assertEquals(1, records.size());
        CallRecord record = records.get(0);
        assertEquals("com.example.service.OrderService", record.getCalleeClass());
        assertEquals("processOrder", record.getCalleeMethod());
        assertEquals("DIRECT", record.getCallType());
    }

    // ==================== TC-CIB-07: trivial 方法过滤 ====================

    @Test
    void testTrivialMethodFiltered() throws Exception {
        tempDir = createTempProject();
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        writeFile(srcDir.resolve("GetterService.java"),
                "package com.example;\n" +
                "public class GetterService {\n" +
                "    private int value;\n" +
                "    void process() {\n" +
                "        int v = getValue();\n" +
                "        setValue(42);\n" +
                "        toString();\n" +
                "        hashCode();\n" +
                "        doRealWork();\n" +
                "    }\n" +
                "    int getValue() { return value; }\n" +
                "    void setValue(int v) { this.value = v; }\n" +
                "    void doRealWork() {}\n" +
                "}\n");

        int count = CallIndexBuilder.build(tempDir);
        // Only doRealWork() should be indexed (getValue, setValue, toString, hashCode filtered)
        assertEquals(1, count);
    }

    // ==================== TC-CIB-08: 空目录 ====================

    @Test
    void testEmptyDirectory() throws Exception {
        tempDir = createTempProject();
        Path srcDir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        // No .java files in the directory
        int count = CallIndexBuilder.build(tempDir);
        assertEquals(0, count);
    }

    // ==================== 多模块路径验证 ====================

    @Test
    void testMultiModulePathClassName() throws Exception {
        tempDir = createTempProject();
        // Simulate multi-module structure: codelens-cli/src/main/java/...
        Path srcDir = tempDir.resolve("codelens-cli/src/main/java/com/example");
        Files.createDirectories(srcDir);

        writeFile(srcDir.resolve("Service.java"),
                "package com.example;\n" +
                "public class Service {\n" +
                "    public void process() {}\n" +
                "}\n");

        writeFile(srcDir.resolve("Controller.java"),
                "package com.example;\n" +
                "import com.example.Service;\n" +
                "public class Controller {\n" +
                "    private Service service;\n" +
                "    void handle() {\n" +
                "        service.process();\n" +
                "    }\n" +
                "}\n");

        int count = CallIndexBuilder.build(tempDir);
        assertEquals(1, count, "Should find 1 call");

        // Verify the caller has correct className
        com.codelens.common.callindex.SQLiteCallIndex idx = new com.codelens.common.callindex.SQLiteCallIndex(
                tempDir.resolve(".codelens/callindex.db").toString());
        try {
            java.util.List<com.codelens.common.callindex.CallRecord> records =
                    idx.queryByCaller("com.example.Controller", "handle");
            assertEquals(1, records.size());
            // calleeClass should be correctly resolved to com.example.Service
            assertEquals("com.example.Service", records.get(0).getCalleeClass(),
                    "Multi-module path should resolve to correct calleeClass");
        } finally {
            idx.close();
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建临时项目目录。
     */
    private Path createTempProject() throws IOException {
        Path dir = Files.createTempDirectory("codelens-test-");
        return dir;
    }

    /**
     * 写入文件内容，确保父目录存在。
     */
    private void writeFile(Path filePath, String content) throws IOException {
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, content.getBytes("UTF-8"));
    }

    /**
     * 递归删除目录。
     */
    private void deleteDir(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDir(f);
                } else {
                    f.delete();
                }
            }
        }
        dir.delete();
    }
}
