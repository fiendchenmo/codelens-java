// SYNC_VERSION: 2026-06-23-v1
// IMPACT: LOGIC_CHANGE
// 维护方：喵呜（CLI端）

package com.codelens.common.db.parser;

import com.codelens.common.db.model.FieldMapEntry;
import com.codelens.common.db.model.FieldMapping;
import com.codelens.common.db.model.SqlOperation;
import com.codelens.common.db.model.SqlType;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MyBatis Mapper XML 解析器。
 * <p>
 * 使用 DOM（javax.xml）解析 Mapper XML 文件，
 * 提取 namespace、SQL 操作标签、resultMap 映射和 SQL 片段引用。
 * </p>
 *
 * <p>核心能力：</p>
 * <ul>
 *   <li>解析 {@code <select>/<insert>/<update>/<delete>} 标签，
 *       提取 id、SQL 文本、parameterType、resultMap/resultType</li>
 *   <li>解析 {@code <resultMap>} 标签，
 *       提取 type、{@code <id>}/{@code <result>} 的 property↔column 映射</li>
 *   <li>提取 {@code <sql id="...">} SQL 片段定义</li>
 *   <li>递归展开 {@code <include refid="...">} 引用</li>
 *   <li>通过搜索原始 XML 文本获取行号</li>
 * </ul>
 */
public class MyBatisXmlParser {

    /** 最大递归展开深度（防止循环引用） */
    private static final int MAX_INCLUDE_DEPTH = 5;

    // SQL 操作标签名
    private static final String TAG_SELECT = "select";
    private static final String TAG_INSERT = "insert";
    private static final String TAG_UPDATE = "update";
    private static final String TAG_DELETE = "delete";

    private static final DocumentBuilderFactory DB_FACTORY;

    static {
        DB_FACTORY = DocumentBuilderFactory.newInstance();
        // 安全：禁用外部实体，防止 XXE
        DB_FACTORY.setExpandEntityReferences(false);
        try {
            DB_FACTORY.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception ignored) {
            // 某些 JDK 版本不支持，忽略
        }
    }

    /**
     * 解析单个 MyBatis XML 文件，提取所有 SQL 操作。
     *
     * @param xmlContent  XML 文件内容
     * @param xmlFilePath XML 文件路径（用于 sourceFile 字段）
     * @return 解析出的 SQL 操作列表
     */
    public static List<SqlOperation> parse(String xmlContent, String xmlFilePath) {
        List<SqlOperation> results = new ArrayList<SqlOperation>();

        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            return results;
        }

        try {
            DocumentBuilder builder = DB_FACTORY.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(
                    xmlContent.getBytes(StandardCharsets.UTF_8)));

            Element root = doc.getDocumentElement();
            String namespace = root.getAttribute("namespace");
            if (namespace == null || namespace.isEmpty()) {
                // 尝试从注释或非标准属性获取
                namespace = "";
            }

            // 1. 先收集 SQL 片段定义
            Map<String, String> sqlFragments = collectSqlFragments(root);

            // 2. 收集 resultMap 定义
            Map<String, FieldMapping> resultMaps = collectResultMaps(root, namespace);

            // 3. 解析 SQL 操作的四种标签
            String[] lines = xmlContent.split("\n");

            for (String tagName : new String[]{TAG_SELECT, TAG_INSERT, TAG_UPDATE, TAG_DELETE}) {
                NodeList nodes = root.getElementsByTagName(tagName);
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element elem = (Element) nodes.item(i);
                    SqlOperation op = parseOperation(elem, namespace, tagName,
                            sqlFragments, resultMaps, xmlFilePath, lines, 0);
                    if (op != null) {
                        results.add(op);
                    }
                }
            }

        } catch (Exception e) {
            // 解析失败时返回空列表（调用方应记录日志）
            System.err.println("[MyBatisXmlParser] Failed to parse " + xmlFilePath + ": " + e.getMessage());
        }

        return results;
    }

    /**
     * 解析项目目录下所有 Mapper XML 文件。
     *
     * @param mapperDir resources/mapper 目录路径
     * @return 所有 XML 文件的解析结果
     */
    public static List<SqlOperation> parseDirectory(String mapperDir) {
        List<SqlOperation> allResults = new ArrayList<SqlOperation>();
        File dir = new File(mapperDir);
        if (!dir.exists() || !dir.isDirectory()) {
            return allResults;
        }

        File[] xmlFiles = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".xml");
            }
        });

        if (xmlFiles != null) {
            for (File xmlFile : xmlFiles) {
                try {
                    String content = new String(Files.readAllBytes(xmlFile.toPath()),
                            StandardCharsets.UTF_8);
                    allResults.addAll(parse(content, xmlFile.getAbsolutePath()));
                } catch (IOException e) {
                    System.err.println("[MyBatisXmlParser] Failed to read "
                            + xmlFile.getAbsolutePath() + ": " + e.getMessage());
                }
            }
        }

        // 同时递归子目录
        File[] subDirs = dir.listFiles(new java.io.FileFilter() {
            @Override
            public boolean accept(File f) { return f.isDirectory(); }
        });
        if (subDirs != null) {
            for (File subDir : subDirs) {
                allResults.addAll(parseDirectory(subDir.getAbsolutePath()));
            }
        }

        return allResults;
    }

    // ─── SQL 片段收集 ────────────────────────────────

    /**
     * 收集 {@code <sql id="...">} 片段定义。
     */
    static Map<String, String> collectSqlFragments(Element root) {
        Map<String, String> fragments = new LinkedHashMap<String, String>();
        NodeList sqlNodes = root.getElementsByTagName("sql");
        for (int i = 0; i < sqlNodes.getLength(); i++) {
            Element sqlElem = (Element) sqlNodes.item(i);
            // 只取当前 mapper 的直接子节点级 <sql>，避免递归嵌套
            if (sqlElem.getParentNode() == root) {
                String id = sqlElem.getAttribute("id");
                String text = getTextContent(sqlElem);
                if (id != null && !id.isEmpty() && text != null && !text.trim().isEmpty()) {
                    fragments.put(id, text.trim());
                }
            }
        }
        return fragments;
    }

    // ─── resultMap 收集 ──────────────────────────────

    /**
     * 收集 {@code <resultMap>} 定义。
     */
    static Map<String, FieldMapping> collectResultMaps(Element root, String namespace) {
        Map<String, FieldMapping> resultMaps = new LinkedHashMap<String, FieldMapping>();
        NodeList rmNodes = root.getElementsByTagName("resultMap");
        for (int i = 0; i < rmNodes.getLength(); i++) {
            Element rmElem = (Element) rmNodes.item(i);
            if (rmElem.getParentNode() != root) {
                continue; // 只取直接子节点
            }
            String id = rmElem.getAttribute("id");
            String type = rmElem.getAttribute("type");
            if (id == null || id.isEmpty()) continue;

            FieldMapping fm = new FieldMapping();
            fm.setResultMapId(id);
            fm.setJavaType(type);

            // 提取 <id> 和 <result> 子元素
            NodeList children = rmElem.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child.getNodeType() != Node.ELEMENT_NODE) continue;
                Element childElem = (Element) child;
                String tagName = childElem.getTagName();
                if ("id".equals(tagName) || "result".equals(tagName)) {
                    String property = childElem.getAttribute("property");
                    String column = childElem.getAttribute("column");
                    if (property != null && !property.isEmpty()
                            && column != null && !column.isEmpty()) {
                        boolean isId = "id".equals(tagName);
                        fm.getEntries().add(new FieldMapEntry(property, column, isId));
                    }
                }
            }

            resultMaps.put(id, fm);
        }
        return resultMaps;
    }

    // ─── 单个操作解析 ────────────────────────────────

    /**
     * 解析单个 SQL 操作标签。
     */
    static SqlOperation parseOperation(Element elem, String namespace, String tagName,
                                        Map<String, String> sqlFragments,
                                        Map<String, FieldMapping> resultMaps,
                                        String xmlFilePath, String[] lines,
                                        int includeDepth) {
        String methodId = elem.getAttribute("id");
        if (methodId == null || methodId.isEmpty()) {
            return null;
        }

        SqlOperation op = new SqlOperation();
        op.setMapperInterface(namespace);
        op.setMethodName(methodId);
        op.setSqlType(tagNameToSqlType(tagName));
        op.setSourceType(SqlOperation.SOURCE_MYBATIS_XML);
        op.setSourceFile(xmlFilePath);
        op.setXmlLine(findLineNumber(lines, tagName, methodId));

        // 获取 resultMap / resultType 引用
        String resultMap = elem.getAttribute("resultMap");
        String resultType = elem.getAttribute("resultType");
        if (resultMap != null && !resultMap.isEmpty()) {
            op.setResultMapId(resultMap);
            FieldMapping fm = resultMaps.get(resultMap);
            if (fm != null) {
                op.setFieldMapping(fm);
            }
        }

        // 提取 SQL 文本
        String rawSql = getTextContent(elem);
        if (rawSql != null) {
            // 展开 <include> 引用
            String expanded = expandIncludes(rawSql.trim(), sqlFragments, 0);
            // 精简（截断到 200 字符）
            String trimmed = expanded.replaceAll("\\s+", " ").trim();
            op.setSqlText(trimmed.length() > 200 ? trimmed.substring(0, 197) + "..." : trimmed);
        } else {
            op.setSqlText("");
        }

        return op;
    }

    // ─── <include> 展开 ──────────────────────────────

    /**
     * 递归展开 {@code <include refid="..."/>} 引用。
     * <p>
     * 支持跨 namespace 引用：{@code <include refid="ns.fragId"/>}。
     * 当前实现仅展开同文件内的片段（跨文件引用不处理）。
     * </p>
     *
     * @param sqlWithIncludes 含 include 标签的 SQL 文本
     * @param fragments       已收集的 SQL 片段映射
     * @param depth           当前递归深度
     * @return 展开后的 SQL 文本
     */
    static String expandIncludes(String sqlWithIncludes, Map<String, String> fragments, int depth) {
        if (depth >= MAX_INCLUDE_DEPTH) {
            return sqlWithIncludes;
        }
        if (fragments == null || fragments.isEmpty()) {
            return sqlWithIncludes;
        }

        // 匹配 <include refid="fragId" /> 或 <include refid="fragId"></include>
        // 支持 namespace 前缀（如 "ns.fragId" → 取 fragId）
        StringBuilder result = new StringBuilder();
        int idx = 0;
        while (idx < sqlWithIncludes.length()) {
            int start = sqlWithIncludes.indexOf("<include", idx);
            if (start < 0) {
                result.append(sqlWithIncludes.substring(idx));
                break;
            }
            result.append(sqlWithIncludes.substring(idx, start));

            // 找到 refid 属性
            int refidStart = sqlWithIncludes.indexOf("refid=\"", start);
            if (refidStart < 0 || refidStart > sqlWithIncludes.indexOf(">", start)) {
                refidStart = sqlWithIncludes.indexOf("refid='", start);
                if (refidStart < 0 || refidStart > sqlWithIncludes.indexOf(">", start)) {
                    // 无法解析，跳过此标签
                    int end = sqlWithIncludes.indexOf(">", start);
                    if (end < 0) {
                        result.append(sqlWithIncludes.substring(idx));
                        break;
                    }
                    idx = end + 1;
                    continue;
                }
                // 单引号
                int quoteEnd = sqlWithIncludes.indexOf('\'', refidStart + 7);
                String refid = sqlWithIncludes.substring(refidStart + 7, quoteEnd);
                // 处理 namespace 前缀
                int dot = refid.lastIndexOf('.');
                String fragKey = dot >= 0 ? refid.substring(dot + 1) : refid;
                String fragment = fragments.get(fragKey);
                if (fragment != null) {
                    result.append(expandIncludes(fragment, fragments, depth + 1));
                }
                // 跳到标签结束
                int tagEnd = sqlWithIncludes.indexOf("/>", quoteEnd);
                if (tagEnd < 0) {
                    tagEnd = sqlWithIncludes.indexOf("</include>", quoteEnd);
                    if (tagEnd < 0) {
                        idx = quoteEnd + 1;
                    } else {
                        idx = tagEnd + "</include>".length();
                    }
                } else {
                    idx = tagEnd + 2;
                }
            } else {
                // 双引号
                int quoteEnd = sqlWithIncludes.indexOf('"', refidStart + 8);
                if (quoteEnd < 0) {
                    idx = start + 1;
                    continue;
                }
                String refid = sqlWithIncludes.substring(refidStart + 8, quoteEnd);
                // 处理 namespace 前缀
                int dot = refid.lastIndexOf('.');
                String fragKey = dot >= 0 ? refid.substring(dot + 1) : refid;
                String fragment = fragments.get(fragKey);
                if (fragment != null) {
                    result.append(expandIncludes(fragment, fragments, depth + 1));
                }
                // 跳到标签结束
                int tagEnd = sqlWithIncludes.indexOf("/>", quoteEnd);
                if (tagEnd < 0) {
                    tagEnd = sqlWithIncludes.indexOf("</include>", quoteEnd);
                    if (tagEnd < 0) {
                        idx = quoteEnd + 1;
                    } else {
                        idx = tagEnd + "</include>".length();
                    }
                } else {
                    idx = tagEnd + 2;
                }
            }
        }

        return result.toString();
    }

    // ─── 辅助方法 ─────────────────────────────────────

    /**
     * 获取元素的文本内容（递归拼接所有文本子节点）。
     */
    static String getTextContent(Element elem) {
        StringBuilder sb = new StringBuilder();
        collectTextContent(elem, sb);
        return sb.toString();
    }

    private static void collectTextContent(Node node, StringBuilder sb) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            switch (child.getNodeType()) {
                case Node.TEXT_NODE:
                    sb.append(child.getTextContent());
                    break;
                case Node.ELEMENT_NODE:
                    // 跳过 MyBatis 动态标签（<if>/<where>/<foreach> 等），
                    // 它们不是 SQL 语法，只包裹 SQL 片段。
                    // 注意：<include> 有意不跳过 —— 虽然它也不是 SQL，
                    // 但 getTextContent 的输出会交给 expandIncludes() 处理，
                    // 后者需要看到 <include refid="..."/> 标记才能展开。
                    // <include> 通常是自闭合标签，无文本子节点，不会引入多余内容。
                    String tag = ((Element) child).getTagName();
                    if (!isMyBatisDynamicTag(tag)) {
                        collectTextContent(child, sb);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * 判断是否为 MyBatis 动态 SQL 标签。
     */
    static boolean isMyBatisDynamicTag(String tagName) {
        return "if".equals(tagName)
                || "where".equals(tagName)
                || "foreach".equals(tagName)
                || "choose".equals(tagName)
                || "when".equals(tagName)
                || "otherwise".equals(tagName)
                || "trim".equals(tagName)
                || "set".equals(tagName)
                || "bind".equals(tagName);
    }

    /**
     * 标签名 → SqlType 映射。
     */
    static SqlType tagNameToSqlType(String tagName) {
        if (TAG_SELECT.equals(tagName)) return SqlType.SELECT;
        if (TAG_INSERT.equals(tagName)) return SqlType.INSERT;
        if (TAG_UPDATE.equals(tagName)) return SqlType.UPDATE;
        if (TAG_DELETE.equals(tagName)) return SqlType.DELETE;
        return SqlType.SELECT; // 默认
    }

    /**
     * 在 XML 行数组中查找指定标签和 id 所在的行号（1-based）。
     */
    static int findLineNumber(String[] lines, String tagName, String methodId) {
        if (lines == null || methodId == null) return 0;
        // 构造两个匹配模式：id="methodId" 和 id='methodId'
        String doubleQuoted = "id=\"" + methodId + "\"";
        String singleQuoted = "id='" + methodId + "'";

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            // 确认该行包含目标标签名
            if (line.contains("<" + tagName) || line.contains("< " + tagName)) {
                if (line.contains(doubleQuoted) || line.contains(singleQuoted)) {
                    return i + 1; // 1-based
                }
            }
            // 处理多行情况：当前行包含标签名，下一行包含 id
            if (i + 1 < lines.length && line.contains("<" + tagName)) {
                String nextLine = lines[i + 1];
                if (nextLine.contains(doubleQuoted) || nextLine.contains(singleQuoted)) {
                    return i + 1;
                }
            }
        }
        return 0;
    }
}
