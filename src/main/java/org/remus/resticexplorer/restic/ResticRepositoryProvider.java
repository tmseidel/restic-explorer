package org.remus.resticexplorer.restic;

import org.remus.resticexplorer.repository.data.ResticRepository;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Provider interface for interacting with restic repositories of different types.
 * Implementations of this interface handle the specifics of different storage backends
 * (S3, local, SFTP, etc.).
 */
public interface ResticRepositoryProvider {

    /**
     * Returns the repository type identifier this provider supports.
     */
    String getType();

    /**
     * Builds the environment variables required to access the repository.
     */
    Map<String, String> buildEnvironment(ResticRepository repository);

    /**
     * Builds the repository URL string for the restic CLI.
     */
    String buildRepositoryUrl(ResticRepository repository);

    /**
     * Returns additional command-line arguments for the restic CLI (e.g. {@code -o sftp.command=...}).
     * Default implementation returns an empty list.
     */
    default List<String> buildExtraArguments(ResticRepository repository) {
        return Collections.emptyList();
    }
}
