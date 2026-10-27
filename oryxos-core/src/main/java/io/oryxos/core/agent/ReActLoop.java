package io.oryxos.core.agent;

import io.oryxos.core.context.PromptBuilder;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.provider.ProviderService;
import io.oryxos.core.session.Session;
import io.oryxos.core.tool.ToolExecutor;

public class ReActLoop {

    private final PromptBuilder prompts;

    private final ProviderService provider;

    private final ToolExecutor tools;

    public ReActLoop(PromptBuilder prompts, ProviderService provider, ToolExecutor tools) {
        this.prompts = prompts;
        this.provider = provider;
        this.tools = tools;
    }

    public String run(Session session, String userMessage, Profile profile) {
        session.append(userMessage);
        for (int iteration = 0; iteration < profile.settings().maxIterations(); iteration++) {
            var response = provider.chat(session.id(), profile, prompts.build(session, profile));
            session.append(response);
            if (response.toolCalls().isEmpty()) {
                return response.content();
            }
            for (var call : response.toolCalls()) {
                session.appendToolResult(call, tools.execute(session.id(), call));
            }
        }
        String reply = "达到最大轮数，已停止";
        session.appendAssistant(reply);
        return reply;
    }
}
