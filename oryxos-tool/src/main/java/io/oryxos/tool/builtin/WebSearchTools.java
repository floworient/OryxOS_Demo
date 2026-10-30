package io.oryxos.tool.builtin;

import com.fasterxml.jackson.databind.DeserializationFeature;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolResult;
import io.oryxos.tool.search.SearchProvider;
import io.oryxos.tool.search.SearchResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.util.json.JsonParser;

public final class WebSearchTools {

    private static final int MAX_INPUT_BYTES = 256 * 1024;

    private static final int MAX_OUTPUT_BYTES = 64 * 1024;

    private static final int MAX_RESULTS = 10;

    private static final String SCHEMA = "{\"type\":\"object\",\"additionalProperties\":false,"
            + "\"properties\":{\"query\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":512},"
            + "\"limit\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":10}},"
            + "\"required\":[\"query\"]}";

    private final SearchProvider provider;

    private final OryxTool tool;

    public WebSearchTools(SearchProvider provider) {
        this.provider = java.util.Objects.requireNonNull(provider);
        this.tool = new OryxTool() {
            public String getName() {
                return "web_search";
            }

            public String getDescription() {
                return "通过已配置的搜索 Provider 查询有界网页摘要结果";
            }

            public String getInputSchema() {
                return SCHEMA;
            }

            public ToolResult execute(String input) {
                return WebSearchTools.this.search(input);
            }
        };
    }

    public OryxTool tool() {
        return tool;
    }

    private ToolResult search(String json) {
        try {
            Input input = parse(json);
            List<SearchResult> source = provider.search(input.query(), input.limit());
            if (source == null) {
                throw new IllegalStateException("SearchProvider 返回 null");
            }
            var results = new ArrayList<Map<String, String>>();
            boolean truncated = source.size() > input.limit();
            for (SearchResult result : source) {
                if (results.size() >= input.limit() || results.size() >= MAX_RESULTS) {
                    truncated = true;
                    break;
                }
                var item = new LinkedHashMap<String, String>();
                item.put("title", truncateUtf8(result.title(), 512));
                item.put("url", truncateUtf8(result.url().toString(), 4096));
                item.put("snippet", truncateUtf8(result.snippet(), 4096));
                var candidate = new ArrayList<>(results);
                candidate.add(Map.copyOf(item));
                if (bytes(candidate, true) > MAX_OUTPUT_BYTES) {
                    truncated = true;
                    break;
                }
                results.add(Map.copyOf(item));
                if (!item.get("title").equals(result.title())
                        || !item.get("url").equals(result.url().toString())
                        || !item.get("snippet").equals(result.snippet())) {
                    truncated = true;
                }
            }
            String content = encode(results, truncated);
            return new ToolResult(true, content, null, truncated);
        }
        catch (RuntimeException | java.io.IOException exception) {
            return new ToolResult(false, "", "网页搜索失败", false);
        }
    }

    private static Input parse(String json) throws java.io.IOException {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) {
            throw new IllegalArgumentException("web_search 输入超过上限");
        }
        Object decoded = JsonParser.getObjectMapper().readerFor(Object.class)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(json);
        if (!(decoded instanceof Map<?, ?> values)
                || !Set.of("query", "limit").containsAll(values.keySet())
                || !values.keySet().contains("query")
                || !(values.get("query") instanceof String query) || query.isBlank()
                || query.length() > 512 || query.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("web_search 输入非法");
        }
        int limit = 5;
        if (values.containsKey("limit")) {
            if (!(values.get("limit") instanceof Number number)
                    || number.doubleValue() != number.intValue()) {
                throw new IllegalArgumentException("web_search limit 非法");
            }
            limit = number.intValue();
        }
        if (limit < 1 || limit > MAX_RESULTS) {
            throw new IllegalArgumentException("web_search limit 越界");
        }
        return new Input(query, limit);
    }

    private static String truncateUtf8(String value, int maxBytes) {
        if (value.getBytes(StandardCharsets.UTF_8).length <= maxBytes) {
            return value;
        }
        var result = new StringBuilder();
        int used = 0;
        for (var iterator = value.codePoints().iterator(); iterator.hasNext();) {
            int codePoint = iterator.nextInt();
            String unit = new String(Character.toChars(codePoint));
            int size = unit.getBytes(StandardCharsets.UTF_8).length;
            if (used + size > maxBytes) {
                break;
            }
            result.append(unit);
            used += size;
        }
        return result.toString();
    }

    private static int bytes(List<Map<String, String>> results, boolean truncated) {
        return encode(results, truncated).getBytes(StandardCharsets.UTF_8).length;
    }

    private static String encode(List<Map<String, String>> results, boolean truncated) {
        var response = new LinkedHashMap<String, Object>();
        response.put("results", results);
        response.put("truncated", truncated);
        return JsonParser.toJson(response);
    }

    private record Input(String query, int limit) {
    }
}
