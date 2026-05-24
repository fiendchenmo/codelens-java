package com.codelens.common.detectors.implicit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConditionalBeanDetector implements ImplicitDependencyDetector {
    private final DetectionConfig config;

    private static final Pattern CONDITIONAL_PATTERN = Pattern.compile("@Conditional");
    private static final Pattern PROFILE_PATTERN = Pattern.compile("@Profile");

    public ConditionalBeanDetector(DetectionConfig config) {
        this.config = config;
    }

    @Override
    public DetectionResult detect(DetectionContext context) {
        if (!config.isEnabled()) {
            return new DetectionResult(DetectionDimension.CONDITIONAL_BEAN, context.getFileName(), Collections.<ImplicitDependency>emptyList());
        }
        String source = context.getSourceCode();
        if (source == null || source.isEmpty()) {
            return new DetectionResult(DetectionDimension.CONDITIONAL_BEAN, context.getFileName(), Collections.<ImplicitDependency>emptyList());
        }

        List<ImplicitDependency> deps = new ArrayList<>();
        if (CONDITIONAL_PATTERN.matcher(source).find()) {
            deps.add(new ImplicitDependency("ConditionalBean", DetectionDimension.CONDITIONAL_BEAN, ConfidenceLevel.MEDIUM, "IMPORT_MATCH"));
        }
        if (PROFILE_PATTERN.matcher(source).find()) {
            deps.add(new ImplicitDependency("ProfileBean", DetectionDimension.CONDITIONAL_BEAN, ConfidenceLevel.MEDIUM, "IMPORT_MATCH"));
        }

        return new DetectionResult(DetectionDimension.CONDITIONAL_BEAN, context.getFileName(), deps);
    }
}
