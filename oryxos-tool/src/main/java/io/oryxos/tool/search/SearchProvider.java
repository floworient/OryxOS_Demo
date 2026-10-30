package io.oryxos.tool.search;

import java.util.List;

@FunctionalInterface
public interface SearchProvider {
    List<SearchResult> search(String query, int limit);
}
