package io.oryxos.tool.sandbox;

/** 仅携带固定类别的授权拒绝，不包含目标、参数或外部异常。 */
public final class SandboxViolationException extends SecurityException {

    public static final String FILE_DENIED = "文件访问被 Sandbox 拒绝";

    public static final String SHELL_DENIED = "Shell 命令被 Sandbox 拒绝";

    public static final String HTTP_DENIED = "HTTP 请求被 Sandbox 拒绝";

    public SandboxViolationException(String message) {
        super(safeMessage(message));
    }

    private static String safeMessage(String message) {
        if (!FILE_DENIED.equals(message) && !SHELL_DENIED.equals(message) && !HTTP_DENIED.equals(message)) {
            throw new IllegalArgumentException("Sandbox 拒绝类别非法");
        }
        return message;
    }
}
