package io.oryxos.core.context;

import java.nio.file.Path;
import java.util.Objects;

public record SkillBinding(String name, String description, Path directory, Path definitionFile) {

    public SkillBinding {
        if (name == null || name.isBlank() || description == null || description.isBlank()) {
            throw new IllegalArgumentException("Skill 名称和说明不能为空");
        }
        directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        definitionFile = Objects.requireNonNull(definitionFile, "definitionFile").toAbsolutePath().normalize();
    }
}
