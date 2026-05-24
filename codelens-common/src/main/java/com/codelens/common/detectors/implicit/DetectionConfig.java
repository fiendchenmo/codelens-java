package com.codelens.common.detectors.implicit;

import java.util.EnumSet;
import java.util.Map;

public class DetectionConfig {
    private boolean enabled = true;
    private EnumSet<DetectionDimension> dimensions = EnumSet.allOf(DetectionDimension.class);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public EnumSet<DetectionDimension> getDimensions() { return dimensions; }
    public void setDimensions(EnumSet<DetectionDimension> dimensions) { this.dimensions = dimensions; }

    public static DetectionConfig fromMap(Map<String, String> overrides) {
        DetectionConfig config = new DetectionConfig();
        if (overrides.containsKey("detection.enabled")) {
            config.setEnabled(Boolean.parseBoolean(overrides.get("detection.enabled")));
        }
        if (overrides.containsKey("detection.dimensions")) {
            String dimsStr = overrides.get("detection.dimensions");
            EnumSet<DetectionDimension> dims = EnumSet.noneOf(DetectionDimension.class);
            for (String s : dimsStr.split(",")) {
                try {
                    dims.add(DetectionDimension.valueOf(s.trim()));
                } catch (IllegalArgumentException ignored) {
                }
            }
            config.setDimensions(dims);
        }
        return config;
    }
}
