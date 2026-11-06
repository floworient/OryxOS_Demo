package io.oryxos.web.agent;

/** 一次性 Agent 调用只公开最终回复。 */
public record InvokeAgentView(String reply) { }
