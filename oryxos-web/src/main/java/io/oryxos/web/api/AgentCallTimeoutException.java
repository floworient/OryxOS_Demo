package io.oryxos.web.api;

/** Agent 同步调用超过公开截止时间。 */
public final class AgentCallTimeoutException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AgentCallTimeoutException() {
        super("Agent 调用超时");
    }
}
