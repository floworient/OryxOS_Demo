package io.oryxos.boot;

import io.oryxos.core.context.ContextBudget;
import io.oryxos.core.profile.Profile;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("oryxos.context")
public record ContextBudgetProperties(
        @DefaultValue("8192") int mainInputBudget,
        @DefaultValue("2048") int mainMaxOutputTokens,
        @DefaultValue("4096") int summaryInputBudget,
        @DefaultValue("384") int summaryMaxOutputTokens,
        @DefaultValue("768") int summaryTextBytes,
        @DefaultValue("1024") int archivalTextBytes,
        @DefaultValue("2048") int toolResultBytes,
        @DefaultValue("4") int summaryTriggerTurns,
        @DefaultValue("2048") int summaryTriggerUnits,
        @DefaultValue("") String summaryProvider,
        @DefaultValue("") String summaryModel) {

    ContextBudget budget() {
        if (summaryProvider.isBlank() != summaryModel.isBlank()) {
            throw new IllegalArgumentException("摘要 Provider 和模型必须同时配置");
        }
        var route = summaryProvider.isBlank() ? null
                : new Profile.ProviderRef(summaryProvider.strip(), summaryModel.strip(), null);
        return new ContextBudget(mainInputBudget, mainMaxOutputTokens, summaryInputBudget, summaryMaxOutputTokens,
                summaryTextBytes, archivalTextBytes, toolResultBytes, summaryTriggerTurns, summaryTriggerUnits, route);
    }
}
