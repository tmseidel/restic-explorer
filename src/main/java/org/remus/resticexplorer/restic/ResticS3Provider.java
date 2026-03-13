package org.remus.resticexplorer.restic;

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
        if (repository.getS3AccessKey() != null) {
            env.put("AWS_ACCESS_KEY_ID", repository.getS3AccessKey());
        }
        if (repository.getS3SecretKey() != null) {
            env.put("AWS_SECRET_ACCESS_KEY", repository.getS3SecretKey());
        }
        if (repository.getS3Region() != null && !repository.getS3Region().isBlank()) {
            env.put("AWS_DEFAULT_REGION", repository.getS3Region());
        }
        env.put("RESTIC_PASSWORD", repository.getRepositoryPassword());
        return env;
    }

    @Override
    public String buildRepositoryUrl(ResticRepository repository) {
        return repository.getUrl();
    }
}
