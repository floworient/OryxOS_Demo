package io.oryxos.core.context;

public final class ContextBudgetExceededException extends RuntimeException {

    public ContextBudgetExceededException() {
        super("模型输入超过上下文预算");
    }
}
