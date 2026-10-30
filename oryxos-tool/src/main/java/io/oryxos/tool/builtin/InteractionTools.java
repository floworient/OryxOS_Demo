package io.oryxos.tool.builtin;

import com.fasterxml.jackson.databind.DeserializationFeature;
import io.oryxos.core.interaction.UserInteraction;
import io.oryxos.core.interaction.UserInteractionContext;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolResult;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.util.json.JsonParser;

/** 由当前调用入口提供交互能力，绝不直接读取进程标准输入。 */
public final class InteractionTools {

    private static final int MAX_INPUT_BYTES = 256 * 1024;

    private static final int MAX_QUESTION_LENGTH = 4096;

    private static final int MAX_ANSWER_BYTES = 64 * 1024;

    private static final String SCHEMA = "{\"type\":\"object\",\"additionalProperties\":false,"
            + "\"properties\":{\"question\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":4096}},"
            + "\"required\":[\"question\"]}";

    private final OryxTool tool = new OryxTool() {
        @Override
        public String getName() {
            return "ask_user";
        }

        @Override
        public String getDescription() {
            return "仅在当前入口支持交互时向用户提一个问题并读取一行回答";
        }

        @Override
        public String getInputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(String jsonInput) {
            return ask(jsonInput);
        }
    };

    public OryxTool tool() {
        return tool;
    }

    private static ToolResult ask(String json) {
        try {
            String question = parse(json);
            UserInteraction interaction = UserInteractionContext.current();
            if (interaction == null) {
                return failed("当前入口不支持交互");
            }
            String answer = interaction.ask(question);
            if (answer == null || answer.getBytes(StandardCharsets.UTF_8).length > MAX_ANSWER_BYTES
                    || answer.indexOf('\n') >= 0 || answer.indexOf('\r') >= 0) {
                return failed("用户回答非法或超过上限");
            }
            return new ToolResult(true, answer, null, false);
        }
        catch (RuntimeException | java.io.IOException exception) {
            return failed("用户交互失败");
        }
    }

    private static String parse(String json) throws java.io.IOException {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) {
            throw new IllegalArgumentException("ask_user 输入超过上限");
        }
        Object decoded = JsonParser.getObjectMapper().readerFor(Object.class)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(json);
        if (!(decoded instanceof Map<?, ?> values) || !values.keySet().equals(Set.of("question"))
                || !(values.get("question") instanceof String question) || question.isBlank()
                || question.length() > MAX_QUESTION_LENGTH || question.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("ask_user 输入非法");
        }
        return question;
    }

    private static ToolResult failed(String message) {
        return new ToolResult(false, "", message, false);
    }
}
