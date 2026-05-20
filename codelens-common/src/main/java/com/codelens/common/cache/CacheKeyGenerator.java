package com.codelens.common.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 缓存 key 生成器。
 * <p>
 * 基于源文件内容 MD5 hash + prompt hash + fileName + model 生成唯一 key。
 * key 格式：{combinedHash}_{fileNameWithoutExt}_{modelOrUnknown}.json
 * <p>
 * combinedHash = md5(sourceCode + promptHash)，因此源文件或 prompt 任一变更都会使 key 改变。
 * model 作为文件名后缀，不同模型不会命中同一缓存。
 */
public class CacheKeyGenerator {

    private static final String HASH_ALGO = "MD5";

    private CacheKeyGenerator() {}

    /**
     * 生成缓存文件名。
     *
     * @param sourceCode 源文件内容
     * @param promptHash SystemPrompt.build() 的 MD5 hash
     * @param filePath   源文件路径
     * @param model      使用的模型名（用于区分不同模型的缓存）
     * @return 缓存文件名，如 "a1b2c3d4_UserServiceImpl_deepseek-v4-flash.json"
     */
    public static String generateFileName(String sourceCode, String promptHash, String filePath, String model) {
        String combined = generateCombinedHash(sourceCode, promptHash);
        String fileName = extractFileName(filePath);
        String modelStr = (model != null && !model.isEmpty()) ? model : "unknown";
        // 模型名去特殊字符，避免文件名问题（只保留字母、数字、短横线、下划线、点）
        modelStr = modelStr.replaceAll("[^a-zA-Z0-9._-]", "_");
        return combined + "_" + fileName + "_" + modelStr + ".json";
    }

    /**
     * 生成源文件内容的 hash。
     *
     * @param sourceCode 源文件内容
     * @return 32 位小写 hex MD5
     */
    public static String generateHash(String sourceCode) {
        try {
            MessageDigest md = MessageDigest.getInstance(HASH_ALGO);
            byte[] digest = md.digest(sourceCode.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("MD5 is guaranteed by Java spec, should never happen", e);
        }
    }

    /**
     * 生成 combined hash（sourceCode + promptHash），用于缓存文件名 key。
     */
    public static String generateCombinedHash(String sourceCode, String promptHash) {
        return generateHash(sourceCode + (promptHash != null ? promptHash : ""));
    }

    /** 从路径中提取不带 .java 的文件名 */
    private static String extractFileName(String filePath) {
        String name = filePath;
        int sep = name.lastIndexOf('/');
        if (sep < 0) sep = name.lastIndexOf('\\');
        if (sep >= 0) name = name.substring(sep + 1);
        if (name.endsWith(".java")) {
            name = name.substring(0, name.length() - 5);
        }
        return name;
    }
}
