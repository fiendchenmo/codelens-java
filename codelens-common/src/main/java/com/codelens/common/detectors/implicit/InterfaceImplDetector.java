package com.codelens.common.detectors.implicit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InterfaceImplDetector implements ImplicitDependencyDetector {
    private final DetectionConfig config;

    private static final Pattern AUTOWIRED_PATTERN =
            Pattern.compile("@Autowired\\s*(?:\\w+\\s+)?(?:private\\s+|public\\s+|protected\\s+)?(\\w+)\\s+\\w+");

    public InterfaceImplDetector(DetectionConfig config) {
        this.config = config;
    }

    @Override
    public DetectionResult detect(DetectionContext context) {
        if (!config.isEnabled()) {
            return new DetectionResult(DetectionDimension.INTERFACE_IMPL, context.getFileName(), Collections.<ImplicitDependency>emptyList());
        }

        Map<String, Object> graphData = context.getGraphQueryResults();

        if (graphData != null && graphData.containsKey("class_nodes.implements_ifs")) {
            return detectFromGraphData(context, graphData);
        }

        if (context.getSource() == DetectionSource.CLI_JAVAPARSER) {
            return detectFromSource(context);
        }

        return new DetectionResult(DetectionDimension.INTERFACE_IMPL, context.getFileName(), Collections.<ImplicitDependency>emptyList());
    }

    private DetectionResult detectFromGraphData(DetectionContext context, Map<String, Object> graphData) {
        List<ImplicitDependency> deps = new ArrayList<>();
        String ifsStr = String.valueOf(graphData.get("class_nodes.implements_ifs"));

        String implClass = null;
        int implCount = 0;
        if (graphData.containsKey("impl_class")) {
            implClass = String.valueOf(graphData.get("impl_class"));
            implCount = 1;
        }
        if (graphData.containsKey("impl_classes")) {
            String classesStr = String.valueOf(graphData.get("impl_classes"));
            String[] classes = classesStr.replaceAll("[\\[\\]\"]", "").split(",");
            implCount = classes.length;
            if (implCount == 1) {
                implClass = classes[0].trim();
            }
        }

        String[] interfaces = ifsStr.replaceAll("[\\[\\]\"]", "").split(",");
        for (String iface : interfaces) {
            iface = iface.trim();
            if (!iface.isEmpty()) {
                ConfidenceLevel confidence;
                if (implCount <= 1) {
                    confidence = ConfidenceLevel.HIGH;
                } else {
                    confidence = ConfidenceLevel.MEDIUM;
                }
                deps.add(new ImplicitDependency(iface, implClass,
                        DetectionDimension.INTERFACE_IMPL, confidence, "PSI", null));
            }
        }

        return new DetectionResult(DetectionDimension.INTERFACE_IMPL, context.getFileName(), deps);
    }

    private DetectionResult detectFromSource(DetectionContext context) {
        String source = context.getSourceCode();
        if (source == null || source.isEmpty()) {
            return new DetectionResult(DetectionDimension.INTERFACE_IMPL, context.getFileName(), Collections.<ImplicitDependency>emptyList());
        }

        List<ImplicitDependency> deps = new ArrayList<>();
        Matcher matcher = AUTOWIRED_PATTERN.matcher(source);
        while (matcher.find()) {
            String target = matcher.group(1);
            deps.add(new ImplicitDependency(target, null,
                    DetectionDimension.INTERFACE_IMPL, ConfidenceLevel.HIGH, "UNRESOLVED", null));
        }

        return new DetectionResult(DetectionDimension.INTERFACE_IMPL, context.getFileName(), deps);
    }
}
