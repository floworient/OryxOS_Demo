package io.oryxos.tool.sandbox;

import io.oryxos.core.profile.Profile;
import java.nio.file.Path;
import java.util.Objects;

public record FileAccessAction(Profile profile, Path requested, FileAccessMode mode) implements SandboxAction {
    public FileAccessAction {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(mode, "mode");
    }
}
