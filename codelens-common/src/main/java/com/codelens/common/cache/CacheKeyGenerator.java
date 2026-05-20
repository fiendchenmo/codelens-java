package com.codelens.common.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 缓存 key 生成器。
 * <p>
 * 基于源文件内容 MD5 hash + 文件名生成唯一 key。
 * key 格式：{sourceHash}_{fileNameWithoutExt}.json
 * <p>
 * MD5 碰撞风险极低（同项目同文件名前缀），满足缓存需求。
 */
public class CacheKeyGenerator {

    private static final String HASH_ALGO = "MD5";

    private CacheKeyGenerator() {}

    /**
     * 生成缓存文件名。
     *
     * @param sourceCode 源文件内容
     * @param filePath   源文件路径
     * @return 缓存文件名，如 "a1b2c3d4_UserServiceImpl.json"
     */
    public static String generateFileName(String sourceCode, String filePath) {
        String hash = generateHash(sourceCode);
        String fileName = extractFileName(filePath);
        return hash + "_" + fileName + ".json";
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
