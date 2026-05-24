package com.codelens.common.detectors.implicit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CompositeImplicitDetector {
    private final DetectionConfig config;

    public CompositeImplicitDetector(DetectionConfig config) {
        this.config = config;
    }

    public List<DetectionResult> detectAll(DetectionContext context) {
        if (!config.isEnabled()) {
            return Collections.emptyList();
        }

        List<DetectionResult> results = new ArrayList<>();
        for (DetectionDimension dim : config.getDimensions()) {
            ImplicitDependencyDetector detector = createDetector(dim);
            if (detector != null) {
                results.add(detector.detect(context));
            }
        }
        return results;
    }

    private ImplicitDependencyDetector createDetector(DetectionDimension dim) {
        switch (dim) {
            case SPRING_INJECTION: return new SpringInjectionDetector(config);
            case INTERFACE_IMPL: return new InterfaceImplDetector(config);
            case EVENT_LISTENER: return new EventListenerDetector(config);
            case CONDITIONAL_BEAN: return new ConditionalBeanDetector(config);
            case REFLECTION_CALL: return new ReflectionCallDetector(config);
            case CONFIG_REFERENCE: return new ConfigReferenceDetector(config);
            default: return null;
        }
    }
}
