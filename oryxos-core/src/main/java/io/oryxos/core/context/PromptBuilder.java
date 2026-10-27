package io.oryxos.core.context;

import io.oryxos.core.profile.Profile;
import io.oryxos.core.session.Session;
import io.oryxos.core.tool.OryxTool;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

public class PromptBuilder {

    private final ContextLoader loader;

    private final Map<String, OryxTool> tools;

    private final Function<OryxTool, ToolCallback> schemas;

    private final BiFunction<Session, Profile, String> memory;

    private final Clock clock;

    public PromptBuilder(ContextLoader loader, Map<String, OryxTool> tools,
            Function<OryxTool, ToolCallback> schemas, BiFunction<Session, Profile, String> memory, Clock clock) {
        this.loader = loader;
        this.tools = Map.copyOf(tools);
        this.schemas = schemas;
        this.memory = memory;
        this.clock = clock;
    }

    public Prompt build(Session session, Profile profile) {
        var messages = new ArrayList<Message>();
        messages.add(new SystemMessage(loader.load(profile) + "\n当前日期时间: " + ZonedDateTime.now(clock)));
        String remembered = memory.apply(session, profile);
        if (remembered != null && !remembered.isBlank()) {
            messages.add(new SystemMessage(remembered));
        }
        List<Message> history = session.messages();
        int start = history.size();
        int turns = 0;
        for (int index = history.size() - 1; index >= 0; index--) {
            if (history.get(index) instanceof UserMessage) {
                turns++;
                if (turns > profile.settings().maxHistoryTurns()) {
                    break;
                }
                start = index;
            }
        }
        messages.addAll(history.subList(start, history.size()));
        var callbacks = profile.tools().stream().map(name -> {
            OryxTool tool = tools.get(name);
            if (tool == null) {
                throw new IllegalStateException("Agent " + profile.name() + " 引用了未注册工具: " + name);
            }
            return schemas.apply(tool);
        }).toList();
        return new Prompt(messages, ToolCallingChatOptions.builder().toolCallbacks(callbacks)
                .internalToolExecutionEnabled(false).build());
    }
}
