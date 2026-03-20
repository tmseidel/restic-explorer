package org.remus.resticexplorer.restic;

import org.remus.resticexplorer.repository.data.RepositoryPropertyKey;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ResticSftpProvider implements ResticRepositoryProvider {

    @Override
    public String getType() {
        return "SFTP";
    }

    @Override
    public Map<String, String> buildEnvironment(ResticRepository repository) {
        Map<String, String> env = new HashMap<>();
        String passwordCommand = repository.getProperty(RepositoryPropertyKey.SFTP_PASSWORD_COMMAND);
        if (passwordCommand != null && !passwordCommand.isBlank()) {
            env.put("RESTIC_PASSWORD_COMMAND", passwordCommand);
        } else {
            env.put("RESTIC_PASSWORD", repository.getRepositoryPassword());
        }
        return env;
    }

    @Override
    public List<String> buildExtraArguments(ResticRepository repository) {
        String sftpCommand = repository.getProperty(RepositoryPropertyKey.SFTP_COMMAND);
        if (sftpCommand != null && !sftpCommand.isBlank()) {
            return List.of("-o", "sftp.command=" + sftpCommand);
        }
        return List.of();
    }

    @Override
    public String buildRepositoryUrl(ResticRepository repository) {
        return repository.getUrl();
    }
}
