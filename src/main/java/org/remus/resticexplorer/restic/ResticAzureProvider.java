package org.remus.resticexplorer.restic;

import org.remus.resticexplorer.repository.data.RepositoryPropertyKey;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ResticAzureProvider implements ResticRepositoryProvider {

    @Override
    public String getType() {
        return "AZURE";
    }

    @Override
    public Map<String, String> buildEnvironment(ResticRepository repository) {
        Map<String, String> env = new HashMap<>();
        String accountName = repository.getProperty(RepositoryPropertyKey.AZURE_ACCOUNT_NAME);
        if (accountName != null && !accountName.isBlank()) {
            env.put("AZURE_ACCOUNT_NAME", accountName);
        }
        String accountKey = repository.getProperty(RepositoryPropertyKey.AZURE_ACCOUNT_KEY);
        if (accountKey != null && !accountKey.isBlank()) {
            env.put("AZURE_ACCOUNT_KEY", accountKey);
        }
        String endpointSuffix = repository.getProperty(RepositoryPropertyKey.AZURE_ENDPOINT_SUFFIX);
        if (endpointSuffix != null && !endpointSuffix.isBlank()) {
            env.put("AZURE_ENDPOINT_SUFFIX", endpointSuffix);
        }
        env.put("RESTIC_PASSWORD", repository.getRepositoryPassword());
        return env;
    }

    @Override
    public String buildRepositoryUrl(ResticRepository repository) {
        return repository.getUrl();
    }
}
