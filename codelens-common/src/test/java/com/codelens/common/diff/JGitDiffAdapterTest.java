package com.codelens.common.diff;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JGitDiffAdapter 单元测试。
 * <p>
 * 覆盖 TC-JDA-01 ~ TC-JDA-10 测试用例。
 * 使用 JGit API 创建临时 git 仓库进行测试。
 * </p>
 */
public class JGitDiffAdapterTest {

    @TempDir
    Path tempDir;

    private Path repoPath;

    @BeforeEach
    void setUp() throws Exception {
        repoPath = tempDir;
        Git.init().setDirectory(repoPath.toFile()).call();
    }

    // ==================== Helper: 添加文件并提交 ====================

    private void addCommit(String path, String content, String msg) throws Exception {
        try (Git git = Git.open(repoPath.toFile())) {
            Path file = repoPath.resolve(path);
            Files.createDirectories(file.getParent());
            Files.write(file, content.getBytes());
            git.add().addFilepattern(path).call();
            git.commit()
                    .setAuthor("test", "t@t")
                    .setCommitter("test", "t@t")
                    .setMessage(msg).call();
        }
    }

    // ==================== Helper: 修改文件并提交 ====================

    private void modifyCommit(String path, String content, String msg) throws Exception {
        try (Git git = Git.open(repoPath.toFile())) {
            Files.write(repoPath.resolve(path), content.getBytes());
            git.add().addFilepattern(path).call();
            git.commit()
                    .setAuthor("test", "t@t")
                    .setCommitter("test", "t@t")
                    .setMessage(msg).call();
        }
    }

    // ==================== Helper: 删除文件并提交 ====================

    private void deleteCommit(String path, String msg) throws Exception {
        try (Git git = Git.open(repoPath.toFile())) {
            git.rm().addFilepattern(path).call();
            git.commit()
                    .setAuthor("test", "t@t")
                    .setCommitter("test", "t@t")
                    .setMessage(msg).call();
        }
    }

    // ==================== TC-JDA-01: 单文件 MODIFIED ====================

    @Test
    void testSingleFileModified() throws Exception {
        addCommit("src/Service.java",
                "public class Service {\n" +
                "    private int count;\n" +
                "    public void processOrder() {\n" +
                "        // process\n" +
                "    }\n" +
                "}\n", "initial");

        modifyCommit("src/Service.java",
                "public class Service {\n" +
                "    private int count;\n" +
                "    public void processOrder(String arg) {\n" +
                "        // process\n" +
                "    }\n" +
                "}\n", "modify");

        List<ChangedFile> files = JGitDiffAdapter.parseDiff(repoPath, "HEAD~1");

        assertEquals(1, files.size());
        ChangedFile f = files.get(0);
        assertEquals("src/Service.java", f.filePath);
        assertEquals("Service", f.className);
        assertEquals(ChangeType.MODIFIED, f.changeType);
        assertEquals(ChangeSignificance.HIGH, f.significance);
        assertNotNull(f.changedMethods);
        assertFalse(f.changedMethods.isEmpty());

        ChangedMethod m = f.changedMethods.get(0);
        assertEquals("processOrder", m.methodName);
        assertEquals(ChangeType.MODIFIED, m.changeType);
        assertTrue(m.oldStartLine > 0, "oldStartLine should be positive");
        assertTrue(m.newStartLine > 0, "newStartLine should be positive");
    }

    // ==================== TC-JDA-02: 新增文件 ADDED ====================

    @Test
    void testNewFileAdded() throws Exception {
        addCommit("src/Base.java", "public class Base {}\n", "initial");
        addCommit("src/NewService.java",
                "public class NewService {\n" +
                "    public void process() {}\n" +
                "}\n", "add file");

        List<ChangedFile> files = JGitDiffAdapter.parseDiff(repoPath, "HEAD~1");

        assertEquals(1, files.size());
        ChangedFile f = files.get(0);
        assertEquals("src/NewService.java", f.filePath);
        assertEquals("NewService", f.className);
        assertEquals(ChangeType.ADDED, f.changeType);
        // ADDED files should have no method-level changes
        assertTrue(f.changedMethods == null || f.changedMethods.isEmpty());
    }

    // ==================== TC-JDA-03: 删除文件 DELETED ====================

    @Test
    void testDeletedFile() throws Exception {
        addCommit("src/OldService.java",
                "public class OldService {}\n", "initial");
        deleteCommit("src/OldService.java", "delete file");

        List<ChangedFile> files = JGitDiffAdapter.parseDiff(repoPath, "HEAD~1");

        assertEquals(1, files.size());
        ChangedFile f = files.get(0);
        assertEquals("src/OldService.java", f.filePath);
        assertEquals("OldService", f.className);
        assertEquals(ChangeType.DELETED, f.changeType);
    }

    // ==================== TC-JDA-04: 多文件变更 ====================

    @Test
    void testMultipleFiles() throws Exception {
        addCommit("src/ServiceA.java", "public class ServiceA {}\n", "init A");
        addCommit("src/ServiceC.java", "public class ServiceC {}\n", "init C");

        // All 3 changes in a single commit
        try (Git git = Git.open(repoPath.toFile())) {
            // Modify ServiceA
            Files.write(repoPath.resolve("src/ServiceA.java"),
                    ("public class ServiceA {\n" +
                     "    public void methodA() {}\n" +
                     "}\n").getBytes());
            git.add().addFilepattern("src/ServiceA.java").call();

            // Add ServiceB
            Path sb = repoPath.resolve("src/ServiceB.java");
            Files.createDirectories(sb.getParent());
            Files.write(sb, ("public class ServiceB {}\n").getBytes());
            git.add().addFilepattern("src/ServiceB.java").call();

            // Delete ServiceC
            git.rm().addFilepattern("src/ServiceC.java").call();

            git.commit()
                    .setAuthor("test", "t@t")
                    .setCommitter("test", "t@t")
                    .setMessage("bulk: modify A, add B, delete C").call();
        }

        List<ChangedFile> files = JGitDiffAdapter.parseDiff(repoPath, "HEAD~1");

        assertEquals(3, files.size());
        // Order may vary; verify by change type
        assertTrue(files.stream().anyMatch(f -> f.changeType == ChangeType.MODIFIED));
        assertTrue(files.stream().anyMatch(f -> f.changeType == ChangeType.ADDED));
        assertTrue(files.stream().anyMatch(f -> f.changeType == ChangeType.DELETED));
    }

    // ==================== TC-JDA-05: 非 Java 文件过滤 ====================

    @Test
    void testNonJavaFileFiltering() throws Exception {
        addCommit("src/config.xml", "<config/>\n", "init xml");
        addCommit("src/Service.java", "public class Service {}\n", "init java");

        // Modify both files in one commit
        try (Git git = Git.open(repoPath.toFile())) {
            Files.write(repoPath.resolve("src/config.xml"), "<config updated/>\n".getBytes());
            git.add().addFilepattern("src/config.xml").call();
            Files.write(repoPath.resolve("src/Service.java"),
                    "public class Service {\n    public void process() {}\n}\n".getBytes());
            git.add().addFilepattern("src/Service.java").call();
            git.commit()
                    .setAuthor("test", "t@t")
                    .setCommitter("test", "t@t")
                    .setMessage("modify both").call();
        }

        List<ChangedFile> files = JGitDiffAdapter.parseDiff(repoPath, "HEAD~1");

        assertEquals(1, files.size());
        assertEquals("src/Service.java", files.get(0).filePath);
    }

    // ==================== TC-JDA-06: 纯 import 变更 → LOW ====================

    @Test
    void testImportOnlyChange() throws Exception {
        addCommit("src/Service.java",
                "import java.util.List;\n" +
                "import java.util.Map;\n" +
                "\n" +
                "public class Service {}\n", "initial");

        modifyCommit("src/Service.java",
                "import java.util.ArrayList;\n" +
                "import java.util.Map;\n" +
                "\n" +
                "public class Service {}\n", "change import only");

        List<ChangedFile> files = JGitDiffAdapter.parseDiff(repoPath, "HEAD~1");

        assertEquals(1, files.size());
        ChangedFile f = files.get(0);
        assertEquals(ChangeType.MODIFIED, f.changeType);
        assertEquals(ChangeSignificance.LOW, f.significance);
        assertTrue(f.changedMethods == null || f.changedMethods.isEmpty());
    }

    // ==================== TC-JDA-07: 多模块路径 className ====================

    @Test
    void testMultiModulePath() throws Exception {
        addCommit("fmp-bill/src/main/java/com/example/Service.java",
                "package com.example;\n" +
                "public class Service {}\n", "initial");

        modifyCommit("fmp-bill/src/main/java/com/example/Service.java",
                "package com.example;\n" +
                "public class Service {\n" +
                "    public void process() {}\n" +
                "}\n", "modify");

        List<ChangedFile> files = JGitDiffAdapter.parseDiff(repoPath, "HEAD~1");

        assertEquals(1, files.size());
        assertEquals("com.example.Service", files.get(0).className);
    }

    // ==================== TC-JDA-08: 重命名文件 RENAME → MODIFIED ====================

    @Test
    void testRenameFile() throws Exception {
        String content = "public class Service {\n" +
                         "    public void process() {}\n" +
                         "}\n";
        addCommit("src/Service.java", content, "initial");

        // Rename: remove old, add new (identical content)
        try (Git git = Git.open(repoPath.toFile())) {
            Files.delete(repoPath.resolve("src/Service.java"));

            Path newFile = repoPath.resolve("src/NewService.java");
            Files.createDirectories(newFile.getParent());
            Files.write(newFile, content.getBytes());

            git.rm().addFilepattern("src/Service.java").call();
            git.add().addFilepattern("src/NewService.java").call();
            git.commit()
                    .setAuthor("test", "t@t")
                    .setCommitter("test", "t@t")
                    .setMessage("rename Service -> NewService").call();
        }

        List<ChangedFile> files = JGitDiffAdapter.parseDiff(repoPath, "HEAD~1");

        // With rename detection, should be 1 RENAME entry (mapped to MODIFIED)
        assertEquals(1, files.size());
        ChangedFile f = files.get(0);
        assertEquals("src/NewService.java", f.filePath);
        assertEquals(ChangeType.MODIFIED, f.changeType);
    }

    // ==================== TC-JDA-09: 非 Java 文件跳过（含二进制）====================

    @Test
    void testBinaryFileSkipped() throws Exception {
        addCommit("lib/data.bin", "binary content\n", "init bin");
        addCommit("lib/lib.jar", "jar content\n", "init jar");

        // Modify both non-Java files
        try (Git git = Git.open(repoPath.toFile())) {
            Files.write(repoPath.resolve("lib/data.bin"), "modified\n".getBytes());
            git.add().addFilepattern("lib/data.bin").call();
            Files.write(repoPath.resolve("lib/lib.jar"), "modified jar\n".getBytes());
            git.add().addFilepattern("lib/lib.jar").call();
            git.commit()
                    .setAuthor("test", "t@t")
                    .setCommitter("test", "t@t")
                    .setMessage("modify binary").call();
        }

        List<ChangedFile> files = JGitDiffAdapter.parseDiff(repoPath, "HEAD~1");
        assertTrue(files.isEmpty(), "Non-Java files should be filtered out");
    }

    // ==================== TC-JDA-10: 空 diff ====================

    @Test
    void testEmptyDiff() throws Exception {
        addCommit("src/Service.java", "public class Service {}\n", "initial");

        // Diff HEAD against itself → no changes
        List<ChangedFile> files = JGitDiffAdapter.parseDiff(repoPath, "HEAD");
        assertTrue(files.isEmpty());
    }

    // ==================== 额外: deriveClassName ====================

    @Test
    void testClassNameDerivation() {
        assertEquals("", JGitDiffAdapter.deriveClassName(""));
        assertEquals("", JGitDiffAdapter.deriveClassName(null));
        assertEquals("com.example.Service",
                JGitDiffAdapter.deriveClassName("src/main/java/com/example/Service.java"));
        assertEquals("com.example.Service",
                JGitDiffAdapter.deriveClassName("src/com/example/Service.java"));
        assertEquals("Service",
                JGitDiffAdapter.deriveClassName("src/Service.java"));
        assertEquals("com.example.Test",
                JGitDiffAdapter.deriveClassName("src/test/java/com/example/Test.java"));
        // Multi-module paths
        assertEquals("com.example.Service",
                JGitDiffAdapter.deriveClassName("fmp-bill/src/main/java/com/example/Service.java"));
        assertEquals("com.example.Controller",
                JGitDiffAdapter.deriveClassName("fmp-bill/src/main/java/com/example/Controller.java"));
        assertEquals("com.example.Test",
                JGitDiffAdapter.deriveClassName("fmp-bill/src/test/java/com/example/Test.java"));
    }
}
