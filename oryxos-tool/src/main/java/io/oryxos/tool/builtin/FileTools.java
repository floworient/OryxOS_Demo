package io.oryxos.tool.builtin;

import com.fasterxml.jackson.databind.DeserializationFeature;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileContext;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolResult;
import io.oryxos.tool.sandbox.FileAccessAction;
import io.oryxos.tool.sandbox.FileAccessMode;
import io.oryxos.tool.sandbox.Sandbox;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.re2j.Pattern;
import org.springframework.ai.util.json.JsonParser;

public final class FileTools {

    private static final int MAX_INPUT_BYTES = 256 * 1024;

    private static final int MAX_FILE_BYTES = 1024 * 1024;

    private static final int MAX_OUTPUT_BYTES = 256 * 1024;

    private static final int MAX_SEARCH_FILES = 10_000;

    private static final long MAX_SEARCH_BYTES = 32L * 1024 * 1024;

    private static final int MAX_RESULTS = 200;

    private static final String PATH_SCHEMA = "{\"type\":\"object\",\"additionalProperties\":false,"
            + "\"properties\":{\"path\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":4096}},"
            + "\"required\":[\"path\"]}";

    private final Sandbox sandbox;

    private final List<OryxTool> tools;

    public FileTools(Sandbox sandbox) {
        this.sandbox = java.util.Objects.requireNonNull(sandbox);
        this.tools = List.of(
                tool("read_file", "读取当前 Agent 或已绑定 Skill 中的 UTF-8 文本文件", PATH_SCHEMA, this::read),
                tool("write_file", "在当前 Agent 目录创建或覆盖 UTF-8 文本文件", schema("content"), this::write),
                tool("list_dir", "列出当前 Agent 或已绑定 Skill 中的目录", PATH_SCHEMA, this::list),
                tool("edit_file", "在当前 Agent 文件中唯一替换一段文本", editSchema(), this::edit),
                tool("grep", "在批准目录中使用 Java 正则搜索 UTF-8 文本", schema("pattern"), this::grep),
                tool("glob", "在批准目录中按 glob 模式查找路径", schema("pattern"), this::glob));
    }

    public List<OryxTool> all() {
        return tools;
    }

    private ToolResult read(String json) throws IOException {
        Map<String, Object> input = input(json, Set.of("path"), Set.of("path"));
        Path path = approved(path(input), FileAccessMode.READ);
        requireRegular(path);
        byte[] bytes = readBounded(path);
        if (bytes.length > MAX_OUTPUT_BYTES) {
            throw new IllegalArgumentException("文件内容超过 read_file 输出上限");
        }
        return success(decodeText(bytes));
    }

    private ToolResult write(String json) throws IOException {
        Map<String, Object> input = input(json, Set.of("path", "content"), Set.of("path", "content"));
        String content = string(input, "content", true);
        byte[] bytes = boundedContent(content);
        Path target = approved(path(input), FileAccessMode.WRITE);
        requireExistingParent(target);
        atomicWrite(target, bytes);
        return success(JsonParser.toJson(Map.of("bytes_written", bytes.length)));
    }

    private ToolResult list(String json) throws IOException {
        Map<String, Object> input = input(json, Set.of("path"), Set.of("path"));
        Path directory = approved(path(input), FileAccessMode.LIST);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("list_dir 目标不是目录");
        }
        List<Path> entries;
        try (var stream = Files.list(directory)) {
            entries = stream.sorted(java.util.Comparator.comparing(FileTools::fileName))
                    .limit(MAX_RESULTS + 1L).toList();
        }
        boolean truncated = entries.size() > MAX_RESULTS;
        var values = new ArrayList<Map<String, Object>>();
        for (Path entry : entries.stream().limit(MAX_RESULTS).toList()) {
            var value = new LinkedHashMap<String, Object>();
            value.put("name", fileName(entry));
            value.put("type", Files.isSymbolicLink(entry) ? "link"
                    : Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS) ? "directory"
                    : Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS) ? "file" : "other");
            if (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                value.put("size", Files.size(entry));
            }
            values.add(value);
        }
        return success(result("entries", values, truncated));
    }

    private ToolResult edit(String json) throws IOException {
        Map<String, Object> input = input(json, Set.of("path", "old_text", "new_text"),
                Set.of("path", "old_text", "new_text"));
        String oldText = string(input, "old_text", false);
        if (oldText.isEmpty()) {
            throw new IllegalArgumentException("edit_file old_text 不能为空");
        }
        String newText = string(input, "new_text", true);
        Path requested = path(input);
        Path readable = approved(requested, FileAccessMode.READ);
        requireRegular(readable);
        String original = decodeText(readBounded(readable));
        int first = original.indexOf(oldText);
        if (first < 0 || original.indexOf(oldText, first + oldText.length()) >= 0) {
            throw new IllegalArgumentException("edit_file old_text 必须恰好出现一次");
        }
        byte[] replacement = boundedContent(original.substring(0, first) + newText
                + original.substring(first + oldText.length()));
        Path writable = approved(requested, FileAccessMode.WRITE);
        requireExistingParent(writable);
        atomicWrite(writable, replacement);
        return success(JsonParser.toJson(Map.of("replacements", 1, "bytes_written", replacement.length)));
    }

    private ToolResult grep(String json) throws IOException {
        Map<String, Object> input = input(json, Set.of("path", "pattern"), Set.of("path", "pattern"));
        Pattern pattern;
        try {
            pattern = Pattern.compile(string(input, "pattern", false));
        }
        catch (RuntimeException exception) {
            throw new IllegalArgumentException("grep pattern 非法", exception);
        }
        Path root = approved(path(input), FileAccessMode.SEARCH);
        var matches = new ArrayList<String>();
        long totalBytes = 0;
        int visited = 0;
        boolean truncated = false;
        for (Path file : searchableFiles(root)) {
            if (++visited > MAX_SEARCH_FILES) {
                truncated = true;
                break;
            }
            long size;
            try {
                size = Files.size(file);
            }
            catch (IOException exception) {
                continue;
            }
            if (size > MAX_FILE_BYTES || totalBytes + size > MAX_SEARCH_BYTES) {
                truncated = totalBytes + size > MAX_SEARCH_BYTES;
                continue;
            }
            totalBytes += size;
            String text;
            try {
                text = decodeText(Files.readAllBytes(file));
            }
            catch (IOException | IllegalArgumentException exception) {
                continue;
            }
            String[] lines = text.split("\\R", -1);
            for (int index = 0; index < lines.length; index++) {
                if (pattern.matcher(lines[index]).find()) {
                    if (matches.size() >= MAX_RESULTS) {
                        truncated = true;
                        break;
                    }
                    matches.add(display(root, file) + ":" + (index + 1) + ":" + lines[index]);
                }
            }
            if (matches.size() >= MAX_RESULTS) {
                truncated = true;
                break;
            }
        }
        return success(result("matches", matches, truncated));
    }

    private ToolResult glob(String json) throws IOException {
        Map<String, Object> input = input(json, Set.of("path", "pattern"), Set.of("path", "pattern"));
        Path root = approved(path(input), FileAccessMode.SEARCH);
        java.nio.file.PathMatcher matcher;
        try {
            matcher = root.getFileSystem().getPathMatcher("glob:" + string(input, "pattern", false));
        }
        catch (RuntimeException exception) {
            throw new IllegalArgumentException("glob pattern 非法", exception);
        }
        var matches = new ArrayList<String>();
        int visited = 0;
        boolean truncated = false;
        for (Path file : searchablePaths(root)) {
            if (++visited > MAX_SEARCH_FILES) {
                truncated = true;
                break;
            }
            Path relative = Files.isDirectory(root) ? root.relativize(file) : file.getFileName();
            if (matcher.matches(relative)) {
                if (matches.size() >= MAX_RESULTS) {
                    truncated = true;
                    break;
                }
                matches.add(normalize(relative));
            }
        }
        return success(result("matches", matches, truncated));
    }

    private List<Path> searchableFiles(Path root) throws IOException {
        return searchablePaths(root).stream()
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isSymbolicLink(path))
                .toList();
    }

    private List<Path> searchablePaths(Path root) throws IOException {
        if (Files.isRegularFile(root, LinkOption.NOFOLLOW_LINKS)) {
            return List.of(root);
        }
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("搜索目标不是普通文件或目录");
        }
        try (var stream = Files.walk(root)) {
            return stream.filter(path -> !path.equals(root) && !Files.isSymbolicLink(path))
                    .sorted(java.util.Comparator.comparing(path -> normalize(root.relativize(path))))
                    .limit(MAX_SEARCH_FILES + 1L)
                    .toList();
        }
    }

    private Path approved(Path requested, FileAccessMode mode) {
        Profile profile = ProfileContext.current();
        if (profile == null) {
            throw new SecurityException("缺少当前 Agent 上下文");
        }
        return sandbox.enforce(new FileAccessAction(profile, requested, mode));
    }

    private static Map<String, Object> input(String json, Set<String> allowed, Set<String> required) {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) {
            throw new IllegalArgumentException("文件工具输入超过上限");
        }
        Object decoded;
        try {
            decoded = JsonParser.getObjectMapper().readerFor(Object.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(json);
        }
        catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("文件工具输入不是合法 JSON object", exception);
        }
        if (!(decoded instanceof Map<?, ?> raw) || !allowed.containsAll(raw.keySet())
                || !raw.keySet().containsAll(required) || raw.size() != allowed.size()) {
            throw new IllegalArgumentException("文件工具输入字段非法");
        }
        var values = new LinkedHashMap<String, Object>();
        raw.forEach((key, value) -> values.put((String) key, value));
        return values;
    }

    private static Path path(Map<String, Object> input) {
        String value = string(input, "path", false);
        if (value.isBlank() || value.length() > 4096 || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("文件路径非法");
        }
        try {
            return Path.of(value);
        }
        catch (RuntimeException exception) {
            throw new IllegalArgumentException("文件路径非法", exception);
        }
    }

    private static String string(Map<String, Object> input, String field, boolean allowEmpty) {
        Object raw = input.get(field);
        if (!(raw instanceof String value) || (!allowEmpty && value.isEmpty())) {
            throw new IllegalArgumentException("字段必须是字符串: " + field);
        }
        return value;
    }

    private static byte[] boundedContent(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("文件内容超过 1 MiB 上限");
        }
        return bytes;
    }

    private static byte[] readBounded(Path path) throws IOException {
        long size = Files.size(path);
        if (size > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("文件超过 1 MiB 上限");
        }
        return Files.readAllBytes(path);
    }

    private static String decodeText(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) {
                throw new IllegalArgumentException("文件不是 UTF-8 文本");
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        }
        catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("文件不是合法 UTF-8 文本", exception);
        }
    }

    private static void requireRegular(Path path) {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("目标不是普通文件");
        }
    }

    private static void requireExistingParent(Path path) {
        Path parent = path.getParent();
        if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(parent)) {
            throw new IllegalArgumentException("写入目标父目录不存在或不安全");
        }
    }

    private static void atomicWrite(Path target, byte[] bytes) throws IOException {
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("写入目标缺少父目录");
        }
        Path realParent = parent.toRealPath();
        Object parentKey = Files.readAttributes(parent, java.nio.file.attribute.BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS).fileKey();
        verifyParent(parent, realParent, parentKey);
        Path temporary = Files.createTempFile(parent, ".oryxos-write-", ".tmp");
        try {
            verifyParent(parent, realParent, parentKey);
            Files.write(temporary, bytes);
            try {
                verifyParent(parent, realParent, parentKey);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("文件系统不支持原子替换", exception);
            }
        }
        finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void verifyParent(Path parent, Path expectedReal, Object expectedKey) throws IOException {
        var attributes = Files.readAttributes(parent, java.nio.file.attribute.BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink()
                || !parent.toRealPath().equals(expectedReal)
                || !java.util.Objects.equals(expectedKey, attributes.fileKey())) {
            throw new IOException("写入期间父目录发生变化");
        }
    }

    private static String result(String field, Object values, boolean truncated) {
        var result = new LinkedHashMap<String, Object>();
        result.put(field, values);
        result.put("truncated", truncated);
        return JsonParser.toJson(result);
    }

    private static String display(Path root, Path file) {
        return Files.isDirectory(root) ? normalize(root.relativize(file)) : fileName(file);
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String fileName(Path path) {
        return java.util.Objects.requireNonNull(path.getFileName()).toString();
    }

    private static ToolResult success(String content) {
        return new ToolResult(true, content, null, false);
    }

    private static OryxTool tool(String name, String description, String schema, Operation operation) {
        return new OryxTool() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public String getInputSchema() {
                return schema;
            }

            @Override
            public ToolResult execute(String jsonInput) {
                try {
                    ToolResult result = operation.execute(jsonInput);
                    if (result.content().getBytes(StandardCharsets.UTF_8).length > MAX_OUTPUT_BYTES) {
                        return failed("文件工具输出超过上限");
                    }
                    return result;
                }
                catch (RuntimeException | IOException exception) {
                    return failed("文件工具执行失败");
                }
            }
        };
    }

    private static String schema(String secondField) {
        return "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{"
                + "\"path\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":4096},"
                + "\"" + secondField + "\":{\"type\":\"string\"}},"
                + "\"required\":[\"path\",\"" + secondField + "\"]}";
    }

    private static String editSchema() {
        return "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{"
                + "\"path\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":4096},"
                + "\"old_text\":{\"type\":\"string\",\"minLength\":1},"
                + "\"new_text\":{\"type\":\"string\"}},"
                + "\"required\":[\"path\",\"old_text\",\"new_text\"]}";
    }

    private static ToolResult failed(String message) {
        return new ToolResult(false, "", message, false);
    }

    @FunctionalInterface
    private interface Operation {
        ToolResult execute(String json) throws IOException;
    }
}
