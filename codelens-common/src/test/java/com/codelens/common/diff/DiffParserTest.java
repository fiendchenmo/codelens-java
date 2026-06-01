package com.codelens.common.diff;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DiffParser 单元测试。
 * <p>
 * 覆盖 TC-DP-01 ~ TC-DP-10 测试用例。
 * TC-DP-10（git仓库解析）使用 @Disabled，仅手动集成测试。
 * </p>
 */
public class DiffParserTest {

    // ==================== TC-DP-01: 单文件 MODIFIED ====================

    @Test
    void testParseSingleFileModified() {
        String diff = "diff --git a/src/Service.java b/src/Service.java\n" +
                "index abc123..def456 100644\n" +
                "--- a/src/Service.java\n" +
                "+++ b/src/Service.java\n" +
                "@@ -10,7 +10,7 @@ public class Service {\n" +
                "     private int count;\n" +
                "     \n" +
                "-    public void processOrder() {\n" +
                "+    public void processOrder(Order order) {\n" +
                "         // process\n" +
                "     }\n" +
                " }\n";

        List<ChangedFile> files = DiffParser.parseDiff(diff);

        assertEquals(1, files.size());
        ChangedFile file = files.get(0);
        assertEquals("src/Service.java", file.filePath);
        assertEquals("Service", file.className);
        assertEquals(ChangeType.MODIFIED, file.changeType);
        assertEquals(ChangeSignificance.HIGH, file.significance);
        assertNotNull(file.changedMethods);
        assertFalse(file.changedMethods.isEmpty());

        ChangedMethod method = file.changedMethods.get(0);
        assertEquals("processOrder", method.methodName);
        assertEquals(ChangeType.MODIFIED, method.changeType);
        assertEquals(10, method.oldStartLine);
        assertEquals(10, method.newStartLine);
    }

    // ==================== TC-DP-02: 新增文件 ====================

    @Test
    void testParseNewFile() {
        String diff = "diff --git a/src/NewService.java b/src/NewService.java\n" +
                "new file mode 100644\n" +
                "--- /dev/null\n" +
                "+++ b/src/NewService.java\n" +
                "@@ -0,0 +1,20 @@\n" +
                "+package com.example;\n" +
                "+\n" +
                "+public class NewService {\n" +
                "+    public void process() {\n" +
                "+    }\n" +
                "+}\n";

        List<ChangedFile> files = DiffParser.parseDiff(diff);

        assertEquals(1, files.size());
        ChangedFile file = files.get(0);
        assertEquals("src/NewService.java", file.filePath);
        assertEquals("NewService", file.className);
        assertEquals(ChangeType.ADDED, file.changeType);
        // ADDED files should have empty changedMethods
        assertTrue(file.changedMethods == null || file.changedMethods.isEmpty());
    }

    // ==================== TC-DP-03: 删除文件 ====================

    @Test
    void testParseDeletedFile() {
        String diff = "diff --git a/src/OldService.java b/src/OldService.java\n" +
                "deleted file mode 100644\n" +
                "--- a/src/OldService.java\n" +
                "+++ /dev/null\n" +
                "@@ -1,13 +0,0 @@\n" +
                "-package com.example;\n" +
                "-\n" +
                "-public class OldService {\n" +
                "-    public void process() {\n" +
                "-    }\n" +
                "-}\n";

        List<ChangedFile> files = DiffParser.parseDiff(diff);

        assertEquals(1, files.size());
        ChangedFile file = files.get(0);
        assertEquals("src/OldService.java", file.filePath);
        assertEquals("OldService", file.className);
        assertEquals(ChangeType.DELETED, file.changeType);
    }

    // ==================== TC-DP-04: 多文件变更 ====================

    @Test
    void testParseMultipleFiles() {
        String diff = "diff --git a/src/ServiceA.java b/src/ServiceA.java\n" +
                "--- a/src/ServiceA.java\n" +
                "+++ b/src/ServiceA.java\n" +
                "@@ -5,7 +5,7 @@\n" +
                " public class ServiceA {\n" +
                "-    public void methodA() {\n" +
                "+    public void methodA(String arg) {\n" +
                "     }\n" +
                " }\n" +
                "diff --git a/src/ServiceB.java b/src/ServiceB.java\n" +
                "new file mode 100644\n" +
                "--- /dev/null\n" +
                "+++ b/src/ServiceB.java\n" +
                "@@ -0,0 +1,10 @@\n" +
                "+package com.example;\n" +
                "+\n" +
                "+public class ServiceB {\n" +
                "+    public void methodB() {\n" +
                "+    }\n" +
                "+}\n" +
                "diff --git a/src/ServiceC.java b/src/ServiceC.java\n" +
                "deleted file mode 100644\n" +
                "--- a/src/ServiceC.java\n" +
                "+++ /dev/null\n" +
                "@@ -1,5 +0,0 @@\n" +
                "-public class ServiceC {\n" +
                "-    public void methodC() {\n" +
                "-    }\n" +
                "-}\n";

        List<ChangedFile> files = DiffParser.parseDiff(diff);

        assertEquals(3, files.size());
        assertEquals(ChangeType.MODIFIED, files.get(0).changeType);
        assertEquals(ChangeType.ADDED, files.get(1).changeType);
        assertEquals(ChangeType.DELETED, files.get(2).changeType);
    }

    // ==================== TC-DP-05: 空 diff ====================

    @Test
    void testParseEmptyDiff() {
        List<ChangedFile> files = DiffParser.parseDiff("");
        assertTrue(files.isEmpty());

        files = DiffParser.parseDiff("   \n\n  ");
        assertTrue(files.isEmpty());

        files = DiffParser.parseDiff(null);
        assertTrue(files.isEmpty());
    }

    // ==================== TC-DP-06: 非 Java 文件过滤 ====================

    @Test
    void testNonJavaFileFiltering() {
        String diff = "diff --git a/src/config.xml b/src/config.xml\n" +
                "--- a/src/config.xml\n" +
                "+++ b/src/config.xml\n" +
                "@@ -1,3 +1,4 @@\n" +
                " <config>\n" +
                "-<old>value</old>\n" +
                "+<new>value</new>\n" +
                " </config>\n" +
                "diff --git a/src/app.properties b/src/app.properties\n" +
                "--- a/src/app.properties\n" +
                "+++ b/src/app.properties\n" +
                "@@ -2,3 +2,4 @@\n" +
                " key1=value1\n" +
                "-key2=oldvalue\n" +
                "+key2=newvalue\n" +
                " key3=value3\n" +
                "diff --git a/src/Service.java b/src/Service.java\n" +
                "--- a/src/Service.java\n" +
                "+++ b/src/Service.java\n" +
                "@@ -5,7 +5,7 @@\n" +
                " public class Service {\n" +
                "-    public void process() {\n" +
                "+    public void process(String input) {\n" +
                "     }\n" +
                " }\n";

        List<ChangedFile> files = DiffParser.parseDiff(diff);

        assertEquals(1, files.size());
        assertEquals("src/Service.java", files.get(0).filePath);
    }

    // ==================== TC-DP-07: 纯 import 变更 ====================

    @Test
    void testImportOnlyChange() {
        String diff = "diff --git a/src/Service.java b/src/Service.java\n" +
                "--- a/src/Service.java\n" +
                "+++ b/src/Service.java\n" +
                "@@ -1,5 +1,5 @@\n" +
                "-import java.util.List;\n" +
                "+import java.util.ArrayList;\n" +
                " import java.util.Map;\n" +
                " import java.util.HashMap;\n" +
                " \n";

        List<ChangedFile> files = DiffParser.parseDiff(diff);

        assertEquals(1, files.size());
        ChangedFile file = files.get(0);
        assertEquals(ChangeType.MODIFIED, file.changeType);
        assertEquals(ChangeSignificance.LOW, file.significance);
        // LOW significance → no method-level changes
        assertTrue(file.changedMethods == null || file.changedMethods.isEmpty());
    }

    // ==================== TC-DP-08: 方法行号匹配 ====================

    @Test
    void testMethodLineNumberMatching() {
        // Hunk shows change at oldStart=15, newStart=15 within processOrder's range (lines 12-25)
        String diff = "diff --git a/src/UserService.java b/src/UserService.java\n" +
                "--- a/src/UserService.java\n" +
                "+++ b/src/UserService.java\n" +
                "@@ -15,7 +15,7 @@\n" +
                "     public void processOrder() {\n" +
                "         String s = \"old\";\n" +
                "-        return;\n" +
                "+        return result;\n" +
                "     }\n" +
                "     \n" +
                "     public void otherMethod() {\n" +
                "     }\n" +
                " }\n";

        List<ChangedFile> files = DiffParser.parseDiff(diff);

        assertEquals(1, files.size());
        ChangedFile file = files.get(0);
        assertEquals(ChangeType.MODIFIED, file.changeType);

        assertNotNull(file.changedMethods);
        assertFalse(file.changedMethods.isEmpty());
        ChangedMethod method = file.changedMethods.get(0);
        assertEquals("processOrder", method.methodName);
        assertEquals(15, method.oldStartLine);
        assertEquals(15, method.newStartLine);
    }

    // ==================== TC-DP-09: 二进制文件跳过 ====================

    @Test
    void testBinaryFileSkipped() {
        String diff = "diff --git a/lib.jar b/lib.jar\n" +
                "Binary files differ\n";

        List<ChangedFile> files = DiffParser.parseDiff(diff);
        assertTrue(files.isEmpty());
    }

    // ==================== TC-DP-10: 从 git 仓库解析（手动测试）====================

    @Test
    @Disabled("仅集成测试时手动执行，需要真实 git 仓库")
    void testParseFromGit() throws Exception {
        Path repoPath = Paths.get(".");
        // Use HEAD~1 to get recent changes
        List<ChangedFile> files = DiffParser.parseDiffFromGit(repoPath, "HEAD~1");
        assertNotNull(files);
        // Should return at least files changed in the last commit
        // (the actual content depends on the repo state)
    }

    // ==================== 额外边界测试 ====================

    @Test
    void testClassNameDerivation() {
        assertEquals("com.example.Service",
                DiffParser.deriveClassName("src/main/java/com/example/Service.java"));
        assertEquals("com.example.Service",
                DiffParser.deriveClassName("src/com/example/Service.java"));
        assertEquals("Service",
                DiffParser.deriveClassName("src/Service.java"));
        assertEquals("com.example.Test",
                DiffParser.deriveClassName("src/test/java/com/example/Test.java"));
        assertEquals("", DiffParser.deriveClassName(""));
        assertEquals("", DiffParser.deriveClassName(null));
        // Multi-module paths
        assertEquals("com.example.Service",
                DiffParser.deriveClassName("fmp-bill/src/main/java/com/example/Service.java"));
        assertEquals("com.example.Controller",
                DiffParser.deriveClassName("fmp-bill/src/main/java/com/example/Controller.java"));
        assertEquals("com.example.Test",
                DiffParser.deriveClassName("fmp-bill/src/test/java/com/example/Test.java"));
    }

    @Test
    void testDiffWithOnlyContextChanges() {
        // Diff with only comment changes
        String diff = "diff --git a/src/Service.java b/src/Service.java\n" +
                "--- a/src/Service.java\n" +
                "+++ b/src/Service.java\n" +
                "@@ -2,7 +2,7 @@\n" +
                " // Old comment\n" +
                "-// TODO: fix this\n" +
                "+// FIXED: this is done\n" +
                " public class Service {\n" +
                " \n";

        List<ChangedFile> files = DiffParser.parseDiff(diff);

        assertEquals(1, files.size());
        assertEquals(ChangeSignificance.LOW, files.get(0).significance);
    }
}
