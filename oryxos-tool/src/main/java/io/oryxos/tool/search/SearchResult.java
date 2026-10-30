package io.oryxos.tool.search;

import java.net.URI;

public record SearchResult(String title, URI url, String snippet) {
    public SearchResult {
        java.util.Objects.requireNonNull(title, "title");
        java.util.Objects.requireNonNull(url, "url");
        java.util.Objects.requireNonNull(snippet, "snippet");
        if (!url.isAbsolute() || url.getHost() == null) {
            throw new IllegalArgumentException("搜索结果 URL 必须是绝对地址");
        }
    }
}
