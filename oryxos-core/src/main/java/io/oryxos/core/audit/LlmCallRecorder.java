package io.oryxos.core.audit;

@FunctionalInterface
public interface LlmCallRecorder {
    LlmCallRecord record(LlmCallRecord record);
}
