package io.oryxos.boot;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("oryxos.memory")
public class MemoryProperties {

    private String backend = "markdown";

    private int archivalMaxCharacters = 4000;

    private int recallMaxResults = 20;

    private int entryMaxCharacters = 4000;

    private String zone = "Asia/Shanghai";

    private Mem0 mem0 = new Mem0();

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

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public Mem0 getMem0() {
        return mem0;
    }

    public void setMem0(Mem0 mem0) {
        this.mem0 = mem0;
    }

    public static class Mem0 {

        private String baseUrl = "";

        private String apiKey = "";

        private Duration connectTimeout = Duration.ofSeconds(3);

        private Duration requestTimeout = Duration.ofSeconds(10);

        private int maxResponseBytes = 1_048_576;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getRequestTimeout() {
            return requestTimeout;
        }

        public void setRequestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
        }

        public int getMaxResponseBytes() {
            return maxResponseBytes;
        }

        public void setMaxResponseBytes(int maxResponseBytes) {
            this.maxResponseBytes = maxResponseBytes;
        }
    }
}
