package org.remus.resticexplorer.restic;

import org.remus.resticexplorer.repository.data.RepositoryPropertyKey;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ResticRcloneProvider implements ResticRepositoryProvider {

    @Override
    public String getType() {
        return "RCLONE";
    }

    @Override
    public Map<String, String> buildEnvironment(ResticRepository repository) {
        Map<String, String> env = new HashMap<>();
        env.put("RESTIC_PASSWORD", repository.getRepositoryPassword());
        return env;
    }

    @Override
    public String buildRepositoryUrl(ResticRepository repository) {
        return repository.getUrl();
    }

    @Override
    public List<String> buildExtraArguments(ResticRepository repository) {
        List<String> args = new ArrayList<>();
        String program = repository.getProperty(RepositoryPropertyKey.RCLONE_PROGRAM);
        if (program != null && !program.isBlank()) {
            args.add("-o");
            args.add("rclone.program=" + program);
        }
        String rcloneArgs = repository.getProperty(RepositoryPropertyKey.RCLONE_ARGS);
        if (rcloneArgs != null && !rcloneArgs.isBlank()) {
            args.add("-o");
            args.add("rclone.args=" + rcloneArgs);
        }
        return args;
    }
}
