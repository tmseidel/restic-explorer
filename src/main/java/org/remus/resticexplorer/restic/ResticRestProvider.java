package org.remus.resticexplorer.restic;

import org.remus.resticexplorer.repository.data.RepositoryPropertyKey;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class ResticRestProvider implements ResticRepositoryProvider {

    @Override
    public String getType() {
        return "REST";
    }

    @Override
    public Map<String, String> buildEnvironment(ResticRepository repository) {
        Map<String, String> env = new HashMap<>();
        env.put("RESTIC_PASSWORD", repository.getRepositoryPassword());
        return env;
    }

    @Override
    public String buildRepositoryUrl(ResticRepository repository) {
        String url = repository.getUrl();
        String username = repository.getProperty(RepositoryPropertyKey.REST_USERNAME);
        String password = repository.getProperty(RepositoryPropertyKey.REST_PASSWORD);

        if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
            // Inject credentials into the URL: rest:http://user:pass@host:port/path
            String prefix = "rest:";
            if (url.startsWith(prefix)) {
                String remainder = url.substring(prefix.length());
                String scheme;
                String hostAndPath;
                if (remainder.startsWith("https://")) {
                    scheme = "https://";
                    hostAndPath = remainder.substring(scheme.length());
                } else if (remainder.startsWith("http://")) {
                    scheme = "http://";
                    hostAndPath = remainder.substring(scheme.length());
                } else {
                    return url;
                }
                return prefix + scheme + URLEncoder.encode(username, StandardCharsets.UTF_8)
                        + ":" + URLEncoder.encode(password, StandardCharsets.UTF_8)
                        + "@" + hostAndPath;
            }
        }
        return url;
    }
}
