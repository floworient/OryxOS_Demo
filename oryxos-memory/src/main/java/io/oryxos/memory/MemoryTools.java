package io.oryxos.memory;

import io.oryxos.core.memory.MemoryEntry;
import io.oryxos.core.memory.MemoryException;
import io.oryxos.core.memory.MemoryScope;
import io.oryxos.core.memory.MemoryService;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileContext;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** Agent 显式保存和召回长期记忆的工具入口。 */
public final class MemoryTools {

    private final int maximumContentCharacters;

    private static final int MAX_KEYWORD_CHARACTERS = 256;

    private final MemoryService memory;

    public MemoryTools(MemoryService memory) {
        this(memory, 4000);
    }

    public MemoryTools(MemoryService memory, int maximumContentCharacters) {
        this.memory = Objects.requireNonNull(memory, "MemoryService 不能为空");
        if (maximumContentCharacters <= 0) {
            throw new IllegalArgumentException("单条记忆上限必须大于 0");
        }
        this.maximumContentCharacters = maximumContentCharacters;
    }

    @Tool(name = "save_memory", description = "保存当前 Agent 的长期记忆，可选择 core 或 archival 分区")
    public String saveMemory(
            @ToolParam(description = "要保存的记忆正文") String content,
            @ToolParam(description = "记忆分区：core 或 archival；省略时为 archival", required = false)
            String scope) {
        Profile profile = requireProfile();
        String normalized = io.oryxos.core.memory.MemoryEntryPolicy
                .normalizeContent(content, maximumContentCharacters);
        MemoryScope target = parseScope(scope);
        memory.save(profile, normalized, target);
        return "记忆已保存到 " + target.name().toLowerCase(Locale.ROOT) + " 分区（"
                + normalized.getBytes(StandardCharsets.UTF_8).length + " bytes）";
    }

    @Tool(name = "recall_memory", description = "按关键词召回当前 Agent 的归档长期记忆")
    public String recallMemory(@ToolParam(description = "召回关键词") String keyword) {
        Profile profile = requireProfile();
        String normalized = requireText(keyword, MAX_KEYWORD_CHARACTERS, "召回关键词");
        List<MemoryEntry> entries = memory.recall(profile, normalized);
        if (entries.isEmpty()) {
            return "未找到匹配的归档记忆";
        }
        return entries.stream()
                .map(entry -> "- [" + entry.createdAt() + "] " + entry.content())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static Profile requireProfile() {
        Profile profile = ProfileContext.current();
        if (profile == null) {
            throw new MemoryException(MemoryException.Code.INVALID_OWNER, "当前没有可用的 Agent 上下文");
        }
        return profile;
    }

    private static MemoryScope parseScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return MemoryScope.ARCHIVAL;
        }
        return switch (scope.trim().toLowerCase(Locale.ROOT)) {
            case "core" -> MemoryScope.CORE;
            case "archival" -> MemoryScope.ARCHIVAL;
            default -> throw new MemoryException(MemoryException.Code.INVALID_SCOPE, "未知记忆分区");
        };
    }

    private static String requireText(String value, int maxCharacters, String name) {
        if (value == null || value.isBlank()) {
            throw new MemoryException(MemoryException.Code.INVALID_CONTENT, name + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxCharacters) {
            throw new MemoryException(MemoryException.Code.INVALID_CONTENT, name + "超过长度上限");
        }
        return normalized;
    }
}
