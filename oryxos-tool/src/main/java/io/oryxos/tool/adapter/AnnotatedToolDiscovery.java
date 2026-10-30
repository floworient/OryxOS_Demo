package io.oryxos.tool.adapter;

import io.oryxos.core.tool.OryxTool;
import java.util.Collection;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

public final class AnnotatedToolDiscovery {

    public List<OryxTool> discover(Collection<?> candidates) {
        Object[] annotated = candidates.stream()
                .filter(java.util.Objects::nonNull)
                .filter(AnnotatedToolDiscovery::hasToolMethod)
                .toArray();
        if (annotated.length == 0) {
            return List.of();
        }
        return java.util.Arrays.stream(MethodToolCallbackProvider.builder()
                        .toolObjects(annotated).build().getToolCallbacks())
                .map(AnnotatedToolAdapter::new)
                .map(OryxTool.class::cast)
                .toList();
    }

    private static boolean hasToolMethod(Object candidate) {
        Class<?> type = org.springframework.util.ClassUtils.getUserClass(candidate);
        return java.util.Arrays.stream(type.getDeclaredMethods())
                .anyMatch(method -> method.isAnnotationPresent(Tool.class));
    }
}
