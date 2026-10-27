package io.oryxos.core.profile;

public final class ProfileContext {

    private static final ThreadLocal<Profile> CURRENT = new ThreadLocal<>();

    private ProfileContext() {
    }

    public static void set(Profile profile) {
        CURRENT.set(profile);
    }

    public static Profile current() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
