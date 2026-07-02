package com.codelens.agent.data;

import java.util.List;
import java.util.Map;

/**
 * 数据访问桥接接口。common 端（codelens-agent）定义，插件端（codelens-plugin）实现。
 * <p>
 * Claude Code 模式：Context Source 的等价物 — 插件端持有分析数据，
 * Agent 通过此接口访问数据，不直接依赖 IntelliJ API。
 * </p>
 *
 * <h3>实现约定</h3>
 * <ul>
 *   <li>所有方法返回 JSON String 或简单类型，零 IntelliJ 依赖</li>
 *   <li>数据不存在时返回 null 或空列表，不抛异常</li>
 *   <li>插件端 {@code IntelliJDataProvider} 桥接 {@code AnalysisResultStore} / {@code DbAnalysisRepository}</li>
 * </ul>
 */
public interface AnalysisDataProvider {

    /**
     * 查询指定文件的 V3 分析结果。
     * @param filePath 文件路径（项目相对路径）
     * @return V3 JSON String，未分析返回 null
     */
    String getV3AnalysisJson(String filePath);

    /**
     * 查询指定文件的调用方数据。
     * @param filePath 文件路径
     * @return key=方法签名, value=调用方列表
     */
    Map<String, List<String>> getCalledBy(String filePath);

    /**
     * 查询指定类的数据库依赖。
     * @param className 完全限定类名
     * @return DB 依赖 JSON String
     */
    String getDbAnalysisJson(String className);

    /**
     * 查询共享指定表的所有类。
     * @param tableName 数据库表名
     * @return 类名列表
     */
    List<String> findClassesByTableName(String tableName);

    /**
     * 查询指定文件的矛盾检测结果。
     * @param filePath 文件路径
     * @return 矛盾报告 JSON String
     */
    String getContradictionReportJson(String filePath);

    /**
     * 查询指定包的摘要信息。
     * @param packageName 包名
     * @return 包摘要 JSON String
     */
    String getPackageSummaryJson(String packageName);

    /**
     * 按关键词搜索方法。
     * @param keyword 搜索关键词
     * @param limit   最大返回数
     * @return 搜索结果 JSON String
     */
    String searchMethods(String keyword, int limit);

    /**
     * 获取项目级摘要（L0 上下文）。
     * @return 项目结构摘要文本
     */
    String getProjectSummary();
}
