package io.oryxos.boot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 管理员额外普通文件根；空列表不扩展 Agent/Skill 的内建权限。 */
@ConfigurationProperties("oryxos.file")
public class FileSandboxProperties {

    private List<String> allowedPaths = new ArrayList<>();

    public List<String> getAllowedPaths() {
        return Collections.unmodifiableList(new ArrayList<>(allowedPaths));
    }

    public void setAllowedPaths(List<String> allowedPaths) {
        this.allowedPaths = allowedPaths == null ? new ArrayList<>() : new ArrayList<>(allowedPaths);
    }
}
