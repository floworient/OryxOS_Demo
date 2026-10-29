package io.oryxos.boot;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("oryxos.http")
public class HttpSandboxProperties {

    private Set<String> allowedDomains = new LinkedHashSet<>();

    public Set<String> getAllowedDomains() {
        return Set.copyOf(allowedDomains);
    }

    public void setAllowedDomains(Set<String> allowedDomains) {
        this.allowedDomains = allowedDomains == null ? new LinkedHashSet<>() : new LinkedHashSet<>(allowedDomains);
    }
}
