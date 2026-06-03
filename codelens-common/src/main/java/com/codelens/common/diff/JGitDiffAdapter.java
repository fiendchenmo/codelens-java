package com.codelens.common.diff;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JGit Diff 适配器。
 * <p>
 * 用 JGit 替代 ProcessBuilder + 正则解析 git diff，直接获取结构化 diff 信息。
 * 文件路径和变更类型来自 JGit API，方法提取和重要性判断复用与 DiffParser 相同的正则逻辑。
 * </p>
 *
 * <p>变更类型映射：</p>
 * <ul>
 *   <li>ADD → ADDED</li>
 *   <li>MODIFY → MODIFIED</li>
 *   <li>DELETE → DELETED</li>
 *   <li>RENAME → MODIFIED（重命名视为修改）</li>
 *   <li>COPY → ADDED（复制视为新增）</li>
 * </ul>
 */
public class JGitDiffAdapter {

    // ==================== Pattern constants (same as DiffParser) ====================

    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@(.*)$");

    private static final Pattern METHOD_DECL = Pattern.compile(
            "\\b(?:public|private|protected|static|final|abstract|synchronized|native|default)\\s+" +
            "(?:\\w+(?:\\[\\])?\\s+)*" +
            "(\\w+)\\s*\\(");

    private static final Pattern IMPORT_LINE = Pattern.compile("^[+-]\\s*import\\s+");
    private static final Pattern COMMENT_LINE = Pattern.compile("^[+-]\\s*(//|/\\*|\\*|\\*/)");
    private static final Pattern BLANK_LINE = Pattern.compile("^[+-]\\s*$");

    private static final Pattern JAVA_EXT = Pattern.compile("\\.java$");

    private static final String[] SOURCE_ROOT_MARKERS = {
            "src/main/java/",
            "src/test/java/"
    };

    private JGitDiffAdapter() {
        // utility class
    }

    /**
     * 解析 git diff，返回变更文件列表。
     *
     * @param repoPath   仓库根目录（包含 .git）
     * @param baseCommit 基准 commit（如 HEAD~3, main, v0.4.0）
     * @return 变更文件列表
     * @throws IllegalArgumentException 如果路径无效或 commit 不存在
     * @throws IOException              如果 JGit 操作失败
     */
    public static List<ChangedFile> parseDiff(Path repoPath, String baseCommit) throws IOException {
        if (repoPath == null || !repoPath.toFile().isDirectory()) {
            throw new IllegalArgumentException("Invalid repository path: " + repoPath);
        }
        if (baseCommit == null || baseCommit.trim().isEmpty()) {
            throw new IllegalArgumentException("Base commit must not be null or empty");
        }

        try (Git git = Git.open(repoPath.toFile())) {
            Repository repo = git.getRepository();

            // Resolve commits
            ObjectId baseId = repo.resolve(baseCommit);
            ObjectId headId = repo.resolve("HEAD");
            if (baseId == null) {
                throw new IllegalArgumentException("Invalid base commit: " + baseCommit);
            }
            if (headId == null) {
                throw new IllegalStateException("Cannot resolve HEAD — empty repository?");
            }

            // Parse commits
            RevCommit baseCommitObj;
            RevCommit headCommitObj;
            try (RevWalk walk = new RevWalk(repo)) {
                baseCommitObj = walk.parseCommit(baseId);
                headCommitObj = walk.parseCommit(headId);
            }

            // Set up tree parsers
            try (ObjectReader reader = repo.newObjectReader()) {
                CanonicalTreeParser baseTree = new CanonicalTreeParser();
                baseTree.reset(reader, baseCommitObj.getTree().getId());
                CanonicalTreeParser headTree = new CanonicalTreeParser();
                headTree.reset(reader, headCommitObj.getTree().getId());

                // Scan diff entries with rename detection
                List<DiffEntry> entries;
                try (DiffFormatter scanner = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                    scanner.setRepository(repo);
                    scanner.setDetectRenames(true);
                    entries = scanner.scan(baseTree, headTree);
                }

                // Process each entry
                return processEntries(entries, repo);
            }

        }
    }

    /**
     * 处理 DiffEntry 列表，转换为 ChangedFile 列表。
     */
    private static List<ChangedFile> processEntries(List<DiffEntry> entries, Repository repo) throws IOException {
        List<ChangedFile> result = new ArrayList<>();
        for (DiffEntry entry : entries) {
            ChangedFile cf = processSingleEntry(entry, repo);
            if (cf != null) {
                result.add(cf);
            }
        }
        return result;
    }

    /**
     * 处理单个 DiffEntry，如果非 Java 文件则返回 null。
     */
    private static ChangedFile processSingleEntry(DiffEntry entry, Repository repo) throws IOException {
        String newPath = entry.getNewPath();
        String oldPath = entry.getOldPath();

        // Skip non-Java files (check both old and new paths for rename cases)
        if (!isJavaFile(newPath) && !isJavaFile(oldPath)) {
            return null;
        }

        // Map change type
        ChangeType changeType = mapChangeType(entry);
        if (changeType == null) {
            return null;
        }

        // Determine file path
        String filePath;
        if (changeType == ChangeType.DELETED) {
            filePath = oldPath;
        } else {
            filePath = newPath;
        }
        if (filePath == null || filePath.isEmpty() || "/dev/null".equals(filePath)) {
            return null;
        }

        String className = deriveClassName(filePath);
        ChangedFile cf = new ChangedFile(filePath, className, changeType);

        // Get formatted diff text for this entry (for hunk/significance/method analysis)
        String entryDiffText = formatEntryDiff(entry, repo);
        if (entryDiffText == null || entryDiffText.trim().isEmpty()) {
            return cf;
        }

        // Parse hunks from the formatted diff text
        List<HunkInfo> hunks = parseHunksFromDiffText(entryDiffText);
        if (hunks.isEmpty()) {
            return cf;
        }

        // Check significance
        if (isLowSignificance(hunks)) {
            cf.significance = ChangeSignificance.LOW;
            return cf;
        }

        // Extract methods (only for MODIFIED files)
        if (changeType == ChangeType.MODIFIED) {
            List<ChangedMethod> methods = extractMethodsFromHunks(hunks, className);
            cf.changedMethods = methods;
        }

        return cf;
    }

    /**
     * 将单个 DiffEntry 格式化为 unified diff 文本。
     */
    private static String formatEntryDiff(DiffEntry entry, Repository repo) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DiffFormatter formatter = new DiffFormatter(baos)) {
            formatter.setRepository(repo);
            formatter.setDiffComparator(RawTextComparator.DEFAULT);
            formatter.setDetectRenames(true);
            formatter.format(entry);
        }
        return baos.toString("UTF-8");
    }

    /**
     * 从 unified diff 文本中解析 hunk 信息。
     */
    private static List<HunkInfo> parseHunksFromDiffText(String diffText) {
        List<HunkInfo> hunks = new ArrayList<>();
        if (diffText == null || diffText.isEmpty()) {
            return hunks;
        }

        String[] lines = diffText.split("\n", -1);
        HunkInfo currentHunk = null;

        for (String line : lines) {
            Matcher hunkMatcher = HUNK_HEADER.matcher(line);
            if (hunkMatcher.matches()) {
                currentHunk = new HunkInfo();
                currentHunk.oldStart = parseIntOrDefault(hunkMatcher.group(1), 0);
                currentHunk.oldCount = parseIntOrDefault(hunkMatcher.group(2), 1);
                currentHunk.newStart = parseIntOrDefault(hunkMatcher.group(3), 0);
                currentHunk.newCount = parseIntOrDefault(hunkMatcher.group(4), 1);
                hunks.add(currentHunk);
            } else if (currentHunk != null) {
                // Collect content lines (context + changes) for method/significance detection
                if (line.startsWith("+") || line.startsWith("-") || line.startsWith(" ")) {
                    currentHunk.contentLines.add(line);
                }
                // Lines starting with "\" (e.g., "\ No newline at end of file") are skipped
            }
        }

        return hunks;
    }

    /**
     * 判断变更是否为低重要性（仅 import/注释/空行变更）。
     */
    private static boolean isLowSignificance(List<HunkInfo> hunks) {
        boolean hasNonTrivialChange = false;
        for (HunkInfo hunk : hunks) {
            for (String line : hunk.contentLines) {
                if (line.startsWith("+") || line.startsWith("-")) {
                    if (!IMPORT_LINE.matcher(line).find()
                            && !COMMENT_LINE.matcher(line).find()
                            && !BLANK_LINE.matcher(line).find()) {
                        hasNonTrivialChange = true;
                    }
                }
            }
        }
        return !hasNonTrivialChange;
    }

    /**
     * 从 hunk 内容中提取变更方法。
     */
    private static List<ChangedMethod> extractMethodsFromHunks(List<HunkInfo> hunks, String className) {
        List<ChangedMethod> methods = new ArrayList<>();
        for (HunkInfo hunk : hunks) {
            String methodName = findMethodNameInHunk(hunk);
            if (methodName != null && !methodName.isEmpty()) {
                ChangedMethod cm = new ChangedMethod();
                cm.className = className;
                cm.methodName = methodName;
                cm.signature = methodName;
                cm.changeType = ChangeType.MODIFIED;
                cm.oldStartLine = hunk.oldStart;
                cm.newStartLine = hunk.newStart;
                methods.add(cm);
            }
        }
        return methods;
    }

    /**
     * 在 hunk 内容行中查找方法声明。
     */
    private static String findMethodNameInHunk(HunkInfo hunk) {
        for (String line : hunk.contentLines) {
            String content = line.length() > 1 ? line.substring(1) : "";
            Matcher matcher = METHOD_DECL.matcher(content);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    /**
     * 将 JGit 的 DiffEntry.ChangeType 映射为项目的 ChangeType。
     */
    private static ChangeType mapChangeType(DiffEntry entry) {
        switch (entry.getChangeType()) {
            case ADD:
                return ChangeType.ADDED;
            case MODIFY:
                return ChangeType.MODIFIED;
            case DELETE:
                return ChangeType.DELETED;
            case RENAME:
                // Rename treated as MODIFIED
                return ChangeType.MODIFIED;
            case COPY:
                // Copy treated as ADDED
                return ChangeType.ADDED;
            default:
                return null;
        }
    }

    /**
     * 判断是否为 Java 文件。
     */
    private static boolean isJavaFile(String filePath) {
        return filePath != null && JAVA_EXT.matcher(filePath).find();
    }

    /**
     * 从文件路径推导类名。
     * 找到 src/main/java/ 或 src/test/java/ 标记并取其之后的部分，
     * 支持多模块路径如 fmp-bill/src/main/java/com/example/Service。
     */
    static String deriveClassName(String filePath) {
        if (filePath == null || filePath.isEmpty()) return "";

        String normalized = filePath.replace('\\', '/');
        String withoutExt = normalized.replaceAll("\\.java$", "");

        // Search for source root marker anywhere in the path (supports multi-module)
        for (String marker : SOURCE_ROOT_MARKERS) {
            int idx = withoutExt.indexOf(marker);
            if (idx >= 0) {
                withoutExt = withoutExt.substring(idx + marker.length());
                return withoutExt.replace("/", ".");
            }
        }

        // Fallback: try to strip leading src/ for flat projects
        if (withoutExt.startsWith("src/")) {
            withoutExt = withoutExt.substring("src/".length());
        }

        return withoutExt.replace("/", ".");
    }

    private static int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // ==================== Internal data structures ====================

    /**
     * 单个 hunk 信息（解析中间态）。
     */
    private static class HunkInfo {
        int oldStart;
        int oldCount;
        int newStart;
        int newCount;
        List<String> contentLines = new ArrayList<>();
    }
}
