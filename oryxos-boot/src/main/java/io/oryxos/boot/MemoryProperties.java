package io.oryxos.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("oryxos.memory")
public class MemoryProperties {

    private String backend = "markdown";

    private int archivalMaxCharacters = 4000;

    private int recallMaxResults = 20;

    private int entryMaxCharacters = 4000;

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public int getArchivalMaxCharacters() {
        return archivalMaxCharacters;
    }

    public void setArchivalMaxCharacters(int archivalMaxCharacters) {
        this.archivalMaxCharacters = archivalMaxCharacters;
    }

    public int getRecallMaxResults() {
        return recallMaxResults;
    }

    public void setRecallMaxResults(int recallMaxResults) {
        this.recallMaxResults = recallMaxResults;
    }

    public int getEntryMaxCharacters() {
        return entryMaxCharacters;
    }

    public void setEntryMaxCharacters(int entryMaxCharacters) {
        this.entryMaxCharacters = entryMaxCharacters;
    }
}
