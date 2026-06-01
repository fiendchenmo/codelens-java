package com.codelens.common.diff;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Git diff 解析器。
 * <p>
 * 解析 unified diff 格式，提取变更文件和方法列表。
 * 仅处理 .java 文件，跳过二进制文件和其他文件类型。
 * </p>
 */
public class DiffParser {

    private static final Pattern DIFF_FILE_HEADER = Pattern.compile("^diff --git a/(.*) b/(.*)$");
    private static final Pattern OLD_FILE_LINE = Pattern.compile("^--- (?:a/(.*)|/dev/null)$");
    private static final Pattern NEW_FILE_LINE = Pattern.compile("^\\+\\+\\+ (?:b/(.*)|/dev/null)$");
    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@(.*)$");
    private static final Pattern BINARY_FILE = Pattern.compile("^Binary files .* differ$");
    private static final Pattern JAVA_EXT = Pattern.compile("\\.java$");

    // Method declaration pattern: modifiers + optional return type + method name + (
    private static final Pattern METHOD_DECL = Pattern.compile(
            "\\b(?:public|private|protected|static|final|abstract|synchronized|native|default)\\s+" +
            "(?:\\w+(?:\\[\\])?\\s+)*" +
            "(\\w+)\\s*\\(");

    // Patterns for significance detection
    private static final Pattern IMPORT_LINE = Pattern.compile("^[+-]\\s*import\\s+");
    private static final Pattern COMMENT_LINE = Pattern.compile("^[+-]\\s*(//|/\\*|\\*|\\*/)");
    private static final Pattern BLANK_LINE = Pattern.compile("^[+-]\\s*$");

    // Source root markers for className derivation (search anywhere in path)
    private static final String[] SOURCE_ROOT_MARKERS = {
            "src/main/java/",
            "src/test/java/"
    };

    private DiffParser() {
        // utility class
    }

    /**
     * 解析 unified diff 字符串，返回变更文件列表。
     *
     * @param diffOutput 完整的 unified diff 输出
     * @return 变更文件列表（空diff返回空列表）
     */
    public static List<ChangedFile> parseDiff(String diffOutput) {
        List<ChangedFile> result = new ArrayList<>();
        if (diffOutput == null || diffOutput.trim().isEmpty()) {
            return result;
        }

        String[] lines = diffOutput.split("\n", -1);
        parseDiffLines(lines, result);
        return result;
    }

    /**
     * 从 git 仓库执行 diff 命令，返回变更文件列表。
     *
     * @param repoPath   项目根目录
     * @param baseCommit 基准commit（如 HEAD~3, main, v0.4.0）
     * @return 变更文件列表
     * @throws IOException           如果 git 命令执行失败
     * @throws IllegalStateException 如果目标目录不是有效的 git 仓库
     */
    public static List<ChangedFile> parseDiffFromGit(Path repoPath, String baseCommit) throws IOException {
        if (repoPath == null || !Files.isDirectory(repoPath)) {
            throw new IllegalArgumentException("Invalid repository path: " + repoPath);
        }
        if (baseCommit == null || baseCommit.trim().isEmpty()) {
            throw new IllegalArgumentException("Base commit must not be null or empty");
        }

        // Verify it's a git repository
        ProcessBuilder checkBuilder = new ProcessBuilder("git", "rev-parse", "--git-dir");
        checkBuilder.directory(repoPath.toFile());
        checkBuilder.redirectErrorStream(true);
        try {
            Process checkProcess = checkBuilder.start();
            int exitCode = checkProcess.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("Not a valid git repository: " + repoPath);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Git check interrupted", e);
        }

        // Execute git diff
        ProcessBuilder builder = new ProcessBuilder("git", "diff", baseCommit, "HEAD");
        builder.directory(repoPath.toFile());
        builder.redirectErrorStream(true);

        Process process = builder.start();
        String diffOutput;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            diffOutput = sb.toString();
        }

        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Git diff interrupted", e);
        }

        if (exitCode != 0) {
            // git diff exits with 1 when there are differences, which is valid
            // It exits with 128 for invalid commit
            if (exitCode == 128) {
                throw new IllegalArgumentException("Invalid base commit: " + baseCommit);
            }
            // exit code 1 means differences found — still valid output
        }

        return parseDiff(diffOutput);
    }

    /**
     * 从 diff 行列表解析，逐文件分段处理。
     */
    private static void parseDiffLines(String[] lines, List<ChangedFile> result) {
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            Matcher fileMatcher = DIFF_FILE_HEADER.matcher(line);
            if (fileMatcher.matches()) {
                // Parse one file section
                FileSection section = new FileSection();
                section.fileHeaderLine = line;
                section.oldPath = fileMatcher.group(1);
                section.newPath = fileMatcher.group(2);

                i = parseFileSection(lines, i + 1, section);

                // Process only .java files, skip binary
                if (!isJavaFile(section.newPath) && !isJavaFile(section.oldPath)) {
                    continue;
                }
                if (section.binary) {
                    continue;
                }

                ChangedFile changedFile = buildChangedFile(section);
                if (changedFile != null) {
                    result.add(changedFile);
                }
            } else {
                i++;
            }
        }
    }

    /**
     * 解析单个文件的所有行，包括 ---/+++ 路径、hunk头和内容。
     *
     * @return 下一段开始的行号
     */
    private static int parseFileSection(String[] lines, int start, FileSection section) {
        int i = start;
        boolean inHunk = false;

        while (i < lines.length) {
            String line = lines[i];

            // Check for next file section
            if (DIFF_FILE_HEADER.matcher(line).matches()) {
                break;
            }

            // Binary file
            if (BINARY_FILE.matcher(line).matches()) {
                section.binary = true;
                i++;
                continue;
            }

            // Old file path
            Matcher oldMatcher = OLD_FILE_LINE.matcher(line);
            if (oldMatcher.matches()) {
                section.oldFilePath = oldMatcher.group(1);
                i++;
                continue;
            }

            // New file path
            Matcher newMatcher = NEW_FILE_LINE.matcher(line);
            if (newMatcher.matches()) {
                section.newFilePath = newMatcher.group(1);
                i++;
                continue;
            }

            // Hunk header
            Matcher hunkMatcher = HUNK_HEADER.matcher(line);
            if (hunkMatcher.matches()) {
                inHunk = true;
                HunkInfo hunk = new HunkInfo();
                hunk.oldStart = parseIntOrDefault(hunkMatcher.group(1), 0);
                hunk.oldCount = parseIntOrDefault(hunkMatcher.group(2), 1);
                hunk.newStart = parseIntOrDefault(hunkMatcher.group(3), 0);
                hunk.newCount = parseIntOrDefault(hunkMatcher.group(4), 1);
                hunk.sectionHeader = hunkMatcher.group(5) != null ? hunkMatcher.group(5).trim() : "";
                section.hunks.add(hunk);
                i++;
                continue;
            }

            // Hunk content lines
            if (inHunk && !section.hunks.isEmpty()) {
                HunkInfo currentHunk = section.hunks.get(section.hunks.size() - 1);
                if (line.startsWith("+") || line.startsWith("-") || line.startsWith(" ")) {
                    currentHunk.contentLines.add(line);
                    i++;
                    continue;
                }
            }

            // Unknown line, skip
            i++;
        }

        return i;
    }

    /**
     * 根据解析的 FileSection 构建 ChangedFile 对象。
     */
    private static ChangedFile buildChangedFile(FileSection section) {
        // Determine the target file path (prefer new path for ADDED/MODIFIED, old path for DELETED)
        String filePath;
        ChangeType changeType = resolveChangeType(section);
        if (changeType == ChangeType.DELETED) {
            filePath = section.oldPath;
        } else {
            filePath = section.newPath;
        }

        if (filePath == null || filePath.isEmpty()) {
            return null;
        }

        String className = deriveClassName(filePath);
        ChangedFile changedFile = new ChangedFile(filePath, className, changeType);

        // Check significance
        if (isLowSignificance(section)) {
            changedFile.significance = ChangeSignificance.LOW;
            // LOW significance files get no method-level changes
            return changedFile;
        }

        // Extract method names from hunk content
        if (changeType == ChangeType.MODIFIED) {
            List<ChangedMethod> methods = extractMethodsFromHunks(section, className);
            changedFile.changedMethods = methods;
        }
        // ADDED and DELETED files don't produce method-level changes

        return changedFile;
    }

    /**
     * 从 hunk 内容中提取变更方法。
     */
    private static List<ChangedMethod> extractMethodsFromHunks(FileSection section, String className) {
        List<ChangedMethod> methods = new ArrayList<>();
        for (HunkInfo hunk : section.hunks) {
            // Scan hunk content lines for method declarations
            String methodName = findMethodNameInHunk(hunk);
            if (methodName != null && !methodName.isEmpty()) {
                ChangedMethod cm = new ChangedMethod();
                cm.className = className;
                cm.methodName = methodName;
                cm.signature = methodName; // simplified signature for now
                cm.changeType = ChangeType.MODIFIED;
                cm.oldStartLine = hunk.oldStart;
                cm.newStartLine = hunk.newStart;
                methods.add(cm);
            }
        }
        return methods;
    }

    /**
     * 在 hunk 内容中查找方法名。
     * 扫描上下文行和变更行，寻找方法声明模式。
     */
    private static String findMethodNameInHunk(HunkInfo hunk) {
        // Scan all lines in the hunk (context + changes) for method declarations
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
     * 判断变更是否为低重要性（仅import/注释/空行变更）。
     */
    private static boolean isLowSignificance(FileSection section) {
        boolean hasNonTrivialChange = false;
        for (HunkInfo hunk : section.hunks) {
            for (String line : hunk.contentLines) {
                if (line.startsWith("+") || line.startsWith("-")) {
                    // Check if this line is trivial (use find() not matches() since these
                    // are partial patterns that match the beginning of the line)
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
     * 根据新旧文件路径判断变更类型。
     */
    private static ChangeType resolveChangeType(FileSection section) {
        boolean oldIsDevNull = section.oldFilePath == null;
        boolean newIsDevNull = section.newFilePath == null;

        if (oldIsDevNull && !newIsDevNull) return ChangeType.ADDED;
        if (!oldIsDevNull && newIsDevNull) return ChangeType.DELETED;
        return ChangeType.MODIFIED;
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

    /**
     * 判断是否为 Java 文件。
     */
    private static boolean isJavaFile(String filePath) {
        return filePath != null && JAVA_EXT.matcher(filePath).find();
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
     * 单个文件的 diff 原始信息（解析中间态）。
     */
    private static class FileSection {
        String fileHeaderLine;
        String oldPath;
        String newPath;
        String oldFilePath;   // from --- line (null if /dev/null)
        String newFilePath;   // from +++ line (null if /dev/null)
        boolean binary;
        List<HunkInfo> hunks = new ArrayList<>();
    }

    /**
     * 单个 hunk 信息。
     */
    private static class HunkInfo {
        int oldStart;
        int oldCount;
        int newStart;
        int newCount;
        String sectionHeader;
        List<String> contentLines = new ArrayList<>();
    }
}
