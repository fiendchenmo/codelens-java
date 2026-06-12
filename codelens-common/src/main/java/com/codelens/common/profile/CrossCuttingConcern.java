package com.codelens.common.profile;

/**
 * 跨切关注点。
 */
public class CrossCuttingConcern {
    /** 关注点类别 */
    private String category; // EXCEPTION_HANDLING / TRANSACTION / LOGGING / SECURITY / CACHING / MESSAGING
    /** 实现机制描述 */
    private String mechanism; // 如 "@ControllerAdvice → GlobalExceptionHandler"
    /** 具体类名 */
    private String location;
    /** 覆盖率 0-1 */
    private double coverage;

    public CrossCuttingConcern() {}

    public CrossCuttingConcern(String category, String mechanism, String location, double coverage) {
        this.category = category;
        this.mechanism = mechanism;
        this.location = location;
        this.coverage = coverage;
    }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getMechanism() { return mechanism; }
    public void setMechanism(String mechanism) { this.mechanism = mechanism; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public double getCoverage() { return coverage; }
    public void setCoverage(double coverage) { this.coverage = coverage; }
}
