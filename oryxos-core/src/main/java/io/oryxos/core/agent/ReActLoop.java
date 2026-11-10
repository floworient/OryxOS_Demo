package io.oryxos.core.agent;

import io.oryxos.core.context.PromptBuilder;
import io.oryxos.core.context.SessionSummarizer;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.provider.ProviderService;
import io.oryxos.core.session.Session;
import io.oryxos.core.tool.ToolExecutor;
import java.time.Instant;
import java.util.concurrent.CancellationException;

public class ReActLoop {

    private final PromptBuilder prompts;

    private final ProviderService provider;

    private final ToolExecutor tools;

    private final SessionSummarizer summarizer;

    public ReActLoop(PromptBuilder prompts, ProviderService provider, ToolExecutor tools) {
        this(prompts, provider, tools, null);
    }

    public ReActLoop(PromptBuilder prompts, ProviderService provider, ToolExecutor tools,
            SessionSummarizer summarizer) {
        this.prompts = prompts;
        this.provider = provider;
        this.tools = tools;
        this.summarizer = summarizer;
    }

    public String run(Session session, String userMessage, Profile profile) {
        checkInterrupted();
        Instant turnTime = Instant.now();
        session.append(userMessage);
        for (int iteration = 0; iteration < profile.settings().maxIterations(); iteration++) {
            checkInterrupted();
            var prepared = prompts.prepare(session, profile, turnTime);
            if (iteration == 0 && summarizer != null) {
                checkInterrupted();
                boolean compacted = summarizer.compact(session, profile, prepared.windowStart());
                checkInterrupted();
                if (compacted) {
                    prepared = prompts.prepare(session, profile, turnTime);
                }
            }
            checkInterrupted();
            var response = provider.chat(session.id(), profile, prepared.prompt());
            checkInterrupted();
            session.append(response);
            if (response.toolCalls().isEmpty()) {
                return response.content();
            }
            for (var call : response.toolCalls()) {
                checkInterrupted();
                var cancellation = AgentService.cancellation();
                var result = cancellation == null ? tools.execute(session.id(), call)
                        : tools.execute(session.id(), call, cancellation);
                session.appendToolResult(call, result);
            }
        }
        checkInterrupted();
        String reply = "达到最大轮数，已停止";
        session.appendAssistant(reply);
        return reply;
    }

    static void checkInterrupted() {
        var cancellation = AgentService.cancellation();
        if (Thread.currentThread().isInterrupted() || cancellation != null && cancellation.getAsBoolean()) {
            throw new CancellationException("Agent 调用已中断");
        }
    }
}
