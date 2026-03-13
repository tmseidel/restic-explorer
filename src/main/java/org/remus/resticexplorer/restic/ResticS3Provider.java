package org.remus.resticexplorer.restic;

import org.remus.resticexplorer.repository.data.RepositoryPropertyKey;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ResticS3Provider implements ResticRepositoryProvider {

    @Override
    public String getType() {
        return "S3";
    }

    @Override
    public Map<String, String> buildEnvironment(ResticRepository repository) {
        Map<String, String> env = new HashMap<>();
        String accessKey = repository.getProperty(RepositoryPropertyKey.S3_ACCESS_KEY);
        if (accessKey != null) {
            env.put("AWS_ACCESS_KEY_ID", accessKey);
        }
        String secretKey = repository.getProperty(RepositoryPropertyKey.S3_SECRET_KEY);
        if (secretKey != null) {
            env.put("AWS_SECRET_ACCESS_KEY", secretKey);
        }
        String region = repository.getProperty(RepositoryPropertyKey.S3_REGION);
        if (region != null && !region.isBlank()) {
            env.put("AWS_DEFAULT_REGION", region);
        }
        env.put("RESTIC_PASSWORD", repository.getRepositoryPassword());
        return env;
    }

    @Override
    public String buildRepositoryUrl(ResticRepository repository) {
        return repository.getUrl();
    }
}
