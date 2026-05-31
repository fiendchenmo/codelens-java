package com.codelens.common.analyzer;

import com.codelens.common.models.ArchitectureLayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 架构分层检测器
 * <p>
 * 通过注解、类名后缀、包名关键词等方式推断类在经典分层架构中的层次。
 * 纯逻辑检测，不依赖任何 LLM 调用。
 * </p>
 */
public class ArchitectureLayerDetector {

    private ArchitectureLayerDetector() {
        // 工具类，禁止实例化
    }

    // ========================================================================
    // 1. 注解检测（优先级 1-5）
    // ========================================================================

    /**
     * 通过注解列表检测架构层次。
     * <p>
     * 优先级（按匹配顺序）：
     * <ol>
     *   <li>@RestController / @Controller → {@link ArchitectureLayer#CONTROLLER}</li>
     *   <li>@Service → {@link ArchitectureLayer#SERVICE}</li>
     *   <li>@Repository → {@link ArchitectureLayer#REPOSITORY}</li>
     *   <li>@Configuration / @ConfigurationProperties → {@link ArchitectureLayer#CONFIG}</li>
     *   <li>@Component + 类名含 Handler/Listener/Consumer → {@link ArchitectureLayer#HANDLER}</li>
     * </ol>
     *
     * @param annotations 类的注解列表（简单类名，不含包路径），可以为 null
     * @param className   类名（用于 @Component + 类名组合判断），可以为 null
     * @return 匹配的架构层，无匹配返回 null
     */
    public static ArchitectureLayer detectByAnnotation(List<String> annotations, String className) {
        if (annotations == null || annotations.isEmpty()) {
            return null;
        }
        // 按优先级依次检查：并非按列表顺序，而是按注解类型优先级
        // 优先级 1：@RestController / @Controller
        if (containsAnyAnnotation(annotations, "RestController", "Controller")) {
            return ArchitectureLayer.CONTROLLER;
        }
        // 优先级 2：@Service
        if (containsAnyAnnotation(annotations, "Service")) {
            return ArchitectureLayer.SERVICE;
        }
        // 优先级 3：@Repository
        if (containsAnyAnnotation(annotations, "Repository")) {
            return ArchitectureLayer.REPOSITORY;
        }
        // 优先级 4：@Configuration / @ConfigurationProperties
        if (containsAnyAnnotation(annotations, "Configuration", "ConfigurationProperties")) {
            return ArchitectureLayer.CONFIG;
        }
        // 优先级 5：@Component + 类名含 Handler/Listener/Consumer
        if (containsAnyAnnotation(annotations, "Component")) {
            if (className != null && containsAnyKeyword(className,
                    "Handler", "Listener", "Consumer")) {
                return ArchitectureLayer.HANDLER;
            }
        }
        return null;
    }

    // ========================================================================
    // 2. 类名后缀检测（优先级 6-13）
    // ========================================================================

    /**
     * 通过类名后缀检测架构层次。
     * <p>
     * 优先级（按匹配顺序）：
     * <ol start="6">
     *   <li>*Controller → {@link ArchitectureLayer#CONTROLLER}</li>
     *   <li>*Service / *ServiceImpl → {@link ArchitectureLayer#SERVICE}</li>
     *   <li>*Repository / *Dao / *Mapper → {@link ArchitectureLayer#REPOSITORY}</li>
     *   <li>*Handler / *Listener / *Consumer → {@link ArchitectureLayer#HANDLER}</li>
     *   <li>*Config / *Configuration → {@link ArchitectureLayer#CONFIG}</li>
     *   <li>*Client / *FeignClient → {@link ArchitectureLayer#CLIENT}</li>
     *   <li>*DTO / *VO / *BO / *Entity / *Model → {@link ArchitectureLayer#MODEL}</li>
     *   <li>*Util / *Utils / *Helper → {@link ArchitectureLayer#UTIL}</li>
     * </ol>
     *
     * @param className 类名（不含包路径），可以为 null
     * @return 匹配的架构层，无匹配返回 null
     */
    public static ArchitectureLayer detectByClassName(String className) {
        if (className == null || className.isEmpty()) {
            return null;
        }

        // 优先级 6
        if (endsWithIgnoreCase(className, "Controller")) {
            return ArchitectureLayer.CONTROLLER;
        }
        // 优先级 7
        if (endsWithIgnoreCase(className, "ServiceImpl")
                || endsWithIgnoreCase(className, "Service")) {
            return ArchitectureLayer.SERVICE;
        }
        // 优先级 8
        if (endsWithIgnoreCase(className, "Repository")
                || endsWithIgnoreCase(className, "Dao")
                || endsWithIgnoreCase(className, "Mapper")) {
            return ArchitectureLayer.REPOSITORY;
        }
        // 优先级 9
        if (endsWithIgnoreCase(className, "Handler")
                || endsWithIgnoreCase(className, "Listener")
                || endsWithIgnoreCase(className, "Consumer")) {
            return ArchitectureLayer.HANDLER;
        }
        // 优先级 10
        if (endsWithIgnoreCase(className, "Configuration")
                || endsWithIgnoreCase(className, "Config")) {
            return ArchitectureLayer.CONFIG;
        }
        // 优先级 11
        if (endsWithIgnoreCase(className, "FeignClient")
                || endsWithIgnoreCase(className, "Client")) {
            return ArchitectureLayer.CLIENT;
        }
        // 优先级 12
        if (endsWithIgnoreCase(className, "DTO")
                || endsWithIgnoreCase(className, "VO")
                || endsWithIgnoreCase(className, "BO")
                || endsWithIgnoreCase(className, "Entity")
                || endsWithIgnoreCase(className, "Model")) {
            return ArchitectureLayer.MODEL;
        }
        // 优先级 13
        if (equalsIgnoreCase(className, "Util")
                || endsWithIgnoreCase(className, "Utils")
                || endsWithIgnoreCase(className, "Helper")) {
            return ArchitectureLayer.UTIL;
        }
        return null;
    }

    // ========================================================================
    // 3. 包名检测（优先级 14-20）
    // ========================================================================

    /**
     * 通过包名关键词检测架构层次。
     * <p>
     * 优先级（按匹配顺序）：
     * <ol start="14">
     *   <li>.controller. / .api. / .web. → {@link ArchitectureLayer#CONTROLLER}</li>
     *   <li>.service. / .biz. → {@link ArchitectureLayer#SERVICE}</li>
     *   <li>.repository. / .dao. / .mapper. → {@link ArchitectureLayer#REPOSITORY}</li>
     *   <li>.handler. / .listener. / .consumer. → {@link ArchitectureLayer#HANDLER}</li>
     *   <li>.config. / .configuration. → {@link ArchitectureLayer#CONFIG}</li>
     *   <li>.client. / .feign. → {@link ArchitectureLayer#CLIENT}</li>
     *   <li>.model. / .entity. / .dto. / .vo. → {@link ArchitectureLayer#MODEL}</li>
     * </ol>
     *
     * @param packageName 完整包名（如 com.example.controller），可以为 null
     * @return 匹配的架构层，无匹配返回 null
     */
    public static ArchitectureLayer detectByPackageName(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return null;
        }
        String pkg = "." + packageName + ".";

        // 优先级 14
        if (pkg.contains(".controller.") || pkg.contains(".api.") || pkg.contains(".web.")) {
            return ArchitectureLayer.CONTROLLER;
        }
        // 优先级 15
        if (pkg.contains(".service.") || pkg.contains(".biz.")) {
            return ArchitectureLayer.SERVICE;
        }
        // 优先级 16
        if (pkg.contains(".repository.") || pkg.contains(".dao.") || pkg.contains(".mapper.")) {
            return ArchitectureLayer.REPOSITORY;
        }
        // 优先级 17
        if (pkg.contains(".handler.") || pkg.contains(".listener.") || pkg.contains(".consumer.")) {
            return ArchitectureLayer.HANDLER;
        }
        // 优先级 18
        if (pkg.contains(".config.") || pkg.contains(".configuration.")) {
            return ArchitectureLayer.CONFIG;
        }
        // 优先级 19
        if (pkg.contains(".client.") || pkg.contains(".feign.")) {
            return ArchitectureLayer.CLIENT;
        }
        // 优先级 20
        if (pkg.contains(".model.") || pkg.contains(".entity.")
                || pkg.contains(".dto.") || pkg.contains(".vo.")) {
            return ArchitectureLayer.MODEL;
        }
        return null;
    }

    // ========================================================================
    // 4. 类级检测（注解 → 类名 → 包名 优先顺序）
    // ========================================================================

    /**
     * 综合检测类的架构层次。
     * <p>
     * 按 注解 → 类名 → 包名 优先级顺序依次尝试，
     * 返回第一个非 null 结果；全部无匹配则返回 {@link ArchitectureLayer#UNKNOWN}。
     *
     * @param annotations 类的注解列表，可以为 null
     * @param className   类名，可以为 null
     * @param packageName 包名，可以为 null
     * @return 检测到的架构层，永不返回 null
     */
    public static ArchitectureLayer detectClassLayer(List<String> annotations,
                                                      String className,
                                                      String packageName) {
        ArchitectureLayer result;

        result = detectByAnnotation(annotations, className);
        if (result != null) {
            return result;
        }

        result = detectByClassName(className);
        if (result != null) {
            return result;
        }

        result = detectByPackageName(packageName);
        if (result != null) {
            return result;
        }

        return ArchitectureLayer.UNKNOWN;
    }

    // ========================================================================
    // 5. 包级检测（众数）
    // ========================================================================

    /**
     * 根据包内各层的分布统计，通过众数判断包的整体架构层次。
     * <p>
     * 取出现次数最多的层；若该层占比小于总数 50%，返回 {@link ArchitectureLayer#UNKNOWN}。
     *
     * @param layerDistribution 层→数量 的分布映射，不可以为 null
     * @return 众数层；占比不足 50% 或映射为空时返回 {@link ArchitectureLayer#UNKNOWN}
     */
    public static ArchitectureLayer detectPackageLayer(Map<ArchitectureLayer, Integer> layerDistribution) {
        if (layerDistribution == null || layerDistribution.isEmpty()) {
            return ArchitectureLayer.UNKNOWN;
        }

        int total = 0;
        ArchitectureLayer mode = ArchitectureLayer.UNKNOWN;
        int maxCount = 0;

        for (Map.Entry<ArchitectureLayer, Integer> entry : layerDistribution.entrySet()) {
            int count = entry.getValue();
            total += count;
            if (count > maxCount) {
                maxCount = count;
                mode = entry.getKey();
            }
        }

        if (total == 0) {
            return ArchitectureLayer.UNKNOWN;
        }

        // 众数占比 >= 50% 才有效
        if (maxCount * 2 >= total) {
            return mode;
        }
        return ArchitectureLayer.UNKNOWN;
    }

    // ========================================================================
    // 6. 包组成分析
    // ========================================================================

    /**
     * 分析包内层分布，返回人类可读的组成字符串。
     * <p>
     * 若众数占比 &ge; 50%，返回 null，表示包类型纯净；
     * 否则返回格式如 {@code "37.5% SERVICE + 37.5% HANDLER + 25.0% MODEL"}，
     * 按占比降序排列。
     *
     * @param layerDistribution 层→数量 的分布映射，不可以为 null
     * @return 组成字符串；纯净包（众数 &ge; 50%）返回 null
     */
    public static String getLayerComposition(Map<ArchitectureLayer, Integer> layerDistribution) {
        if (layerDistribution == null || layerDistribution.isEmpty()) {
            return null;
        }

        // 先算总数和众数
        int total = 0;
        ArchitectureLayer mode = ArchitectureLayer.UNKNOWN;
        int maxCount = 0;

        for (Map.Entry<ArchitectureLayer, Integer> entry : layerDistribution.entrySet()) {
            int count = entry.getValue();
            total += count;
            if (count > maxCount) {
                maxCount = count;
                mode = entry.getKey();
            }
        }

        if (total == 0) {
            return null;
        }

        // 众数 >= 50% → 纯净，返回 null
        if (maxCount * 2 >= total) {
            return null;
        }

        // 按数量降序排列
        List<Map.Entry<ArchitectureLayer, Integer>> sorted = new ArrayList<>(layerDistribution.entrySet());
        sorted.sort(Map.Entry.<ArchitectureLayer, Integer>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()));

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<ArchitectureLayer, Integer> entry : sorted) {
            double pct = entry.getValue() * 100.0 / total;
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(String.format("%.1f%%", pct));
            sb.append(" ").append(entry.getKey());
        }
        return sb.toString();
    }

    // ========================================================================
    // 内部工具
    // ========================================================================

    /**
     * 检查字符串是否以指定后缀结尾（忽略大小写）。
     */
    private static boolean endsWithIgnoreCase(String str, String suffix) {
        return str.length() >= suffix.length()
                && str.regionMatches(true, str.length() - suffix.length(), suffix, 0, suffix.length());
    }

    /**
     * 检查字符串是否等于指定字符串（忽略大小写）。
     */
    private static boolean equalsIgnoreCase(String str, String other) {
        return str.equalsIgnoreCase(other);
    }

    /**
     * 检查字符串是否包含任一关键词。
     */
    private static boolean containsAnyKeyword(String str, String... keywords) {
        if (str == null) {
            return false;
        }
        for (String kw : keywords) {
            if (str.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查注解列表中是否包含任一指定注解名称。
     */
    private static boolean containsAnyAnnotation(List<String> annotations, String... names) {
        for (String ann : annotations) {
            if (ann == null) {
                continue;
            }
            for (String name : names) {
                if (name.equals(ann)) {
                    return true;
                }
            }
        }
        return false;
    }
}
