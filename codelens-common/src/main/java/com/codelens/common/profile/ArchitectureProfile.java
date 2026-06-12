package com.codelens.common.profile;

import java.util.List;
import java.util.Map;

/**
 * 项目架构画像。
 * <p>
 * 由 ArchitectureProfileInferrer 通过纯规则推断生成，零 LLM 调用。
 * 存储: .codelens/architecture_profile.json
 */
public class ArchitectureProfile {

    /** 架构模式 */
    private ArchitecturePattern architecturePattern;

    /** 推断置信度 */
    private ArchitecturePattern.Confidence confidence;

    /** 分层规则：层名 → 允许的下游层列表 */
    private Map<String, List<String>> layerRules;

    /** 跨切关注点 */
    private List<CrossCuttingConcern> crossCuttingConcerns;

    /** 架构层分布：层名 → 类数 */
    private Map<String, Integer> layerDistribution;

    /** 项目约束 */
    private List<Constraint> constraints;

    /** 画像元信息 */
    private ProfileMeta meta;

    public ArchitecturePattern getArchitecturePattern() { return architecturePattern; }
    public void setArchitecturePattern(ArchitecturePattern architecturePattern) { this.architecturePattern = architecturePattern; }

    public ArchitecturePattern.Confidence getConfidence() { return confidence; }
    public void setConfidence(ArchitecturePattern.Confidence confidence) { this.confidence = confidence; }

    public Map<String, List<String>> getLayerRules() { return layerRules; }
    public void setLayerRules(Map<String, List<String>> layerRules) { this.layerRules = layerRules; }

    public List<CrossCuttingConcern> getCrossCuttingConcerns() { return crossCuttingConcerns; }
    public void setCrossCuttingConcerns(List<CrossCuttingConcern> crossCuttingConcerns) { this.crossCuttingConcerns = crossCuttingConcerns; }

    public Map<String, Integer> getLayerDistribution() { return layerDistribution; }
    public void setLayerDistribution(Map<String, Integer> layerDistribution) { this.layerDistribution = layerDistribution; }

    public List<Constraint> getConstraints() { return constraints; }
    public void setConstraints(List<Constraint> constraints) { this.constraints = constraints; }

    public ProfileMeta getMeta() { return meta; }
    public void setMeta(ProfileMeta meta) { this.meta = meta; }
}
