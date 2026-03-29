package org.remus.resticexplorer.restic;

import org.remus.resticexplorer.repository.data.RepositoryPropertyKey;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.springframework.stereotype.Component;

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
        String username = repository.getProperty(RepositoryPropertyKey.REST_USERNAME);
        String password = repository.getProperty(RepositoryPropertyKey.REST_PASSWORD);
        if (username != null && !username.isBlank()) {
            env.put("RESTIC_REST_USERNAME", username);
        }
        if (password != null && !password.isBlank()) {
            env.put("RESTIC_REST_PASSWORD", password);
        }
        return env;
    }

    @Override
    public String buildRepositoryUrl(ResticRepository repository) {
        return repository.getUrl();
    }
}
