package io.oryxos.core.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 所有长期记忆后端共享的正文、排序和上下文窗口规则。 */
public final class MemoryEntryPolicy {

    public static final Comparator<MemoryEntry> STABLE_ORDER = Comparator
            .comparing(MemoryEntry::createdAt).thenComparing(MemoryEntry::id);

    private MemoryEntryPolicy() {
    }

    public static String normalizeContent(String content, int maximumCharacters) {
        if (content == null) {
            throw invalidContent();
        }
        String normalized = content.replaceAll("[\\r\\n]+", " ")
                .replaceAll("[\\p{Z}\\s]+", " ").trim();
        if (normalized.isEmpty() || normalized.length() > maximumCharacters) {
            throw invalidContent();
        }
        return normalized;
    }

    public static List<MemoryEntry> stable(List<MemoryEntry> entries) {
        return entries.stream().sorted(STABLE_ORDER).toList();
    }

    public static List<MemoryEntry> newestWithinPromptLimit(
            List<MemoryEntry> entries, int maximumCharacters) {
        List<MemoryEntry> ordered = stable(entries);
        List<MemoryEntry> selected = new ArrayList<>();
        int used = 0;
        for (int index = ordered.size() - 1; index >= 0; index--) {
            MemoryEntry entry = ordered.get(index);
            int length = promptLineCharacters(entry.content());
            if (used + length > maximumCharacters) {
                break;
            }
            selected.add(entry);
            used += length;
        }
        selected.sort(STABLE_ORDER);
        return List.copyOf(selected);
    }

    public static List<MemoryEntry> latestKeywordMatches(
            List<MemoryEntry> entries, String keyword, int maximumResults) {
        if (keyword == null || keyword.isBlank()) {
            throw new MemoryException(MemoryException.Code.INVALID_CONTENT, "召回关键词不能为空");
        }
        String query = keyword.trim().toLowerCase(Locale.ROOT);
        List<MemoryEntry> matches = stable(entries).stream()
                .filter(entry -> entry.content().toLowerCase(Locale.ROOT).contains(query))
                .toList();
        return List.copyOf(matches.subList(Math.max(0, matches.size() - maximumResults), matches.size()));
    }

    public static int promptLineCharacters(String content) {
        int escaped = 0;
        for (int index = 0; index < content.length(); index++) {
            escaped += switch (content.charAt(index)) {
                case '&' -> 5;
                case '<', '>' -> 4;
                default -> 1;
            };
        }
        return 2 + escaped + 1;
    }

    private static MemoryException invalidContent() {
        return new MemoryException(MemoryException.Code.INVALID_CONTENT, "记忆内容为空或超过长度上限");
    }
}
