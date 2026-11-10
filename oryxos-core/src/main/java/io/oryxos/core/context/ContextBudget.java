package io.oryxos.core.context;

import io.oryxos.core.profile.Profile;

public record ContextBudget(int mainInputBudget, int mainMaxOutputTokens, int summaryInputBudget,
        int summaryMaxOutputTokens, int summaryTextBytes, int archivalTextBytes, int toolResultBytes,
        int summaryTriggerTurns, int summaryTriggerUnits, Profile.ProviderRef summaryRoute) {

    private static final int SUMMARY_FRAMING_RESERVE = 640;

    public static final ContextBudget DEFAULT = new ContextBudget(
            8192, 2048, 4096, 384, 768, 1024, 2048, 4, 2048, null);

    public ContextBudget {
        if (mainMaxOutputTokens <= 0 || summaryMaxOutputTokens <= 0 || summaryInputBudget >= mainInputBudget
                || summaryTextBytes < 128 || (long) summaryInputBudget - summaryTextBytes < SUMMARY_FRAMING_RESERVE
                || archivalTextBytes < 0 || archivalTextBytes >= mainInputBudget
                || toolResultBytes < 128 || toolResultBytes >= mainInputBudget
                || summaryTriggerTurns <= 0 || summaryTriggerUnits <= 0 || summaryTriggerUnits > summaryInputBudget) {
            throw new IllegalArgumentException("oryxos.context 的配额组合无效");
        }
    }
}
