package io.oryxos.core.interaction;

import java.util.Objects;

public final class UserInteractionContext {

    private static final ThreadLocal<UserInteraction> CURRENT = new ThreadLocal<>();

    private UserInteractionContext() {
    }

    public static void set(UserInteraction interaction) {
        CURRENT.set(Objects.requireNonNull(interaction, "interaction"));
    }

    public static UserInteraction current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
