package org.remus.resticexplorer.repository.data;

import lombok.Getter;

/**
 * Enum representing configuration property keys for repository backends.
 * Each repository type defines which keys it requires.
 * Keys marked as sensitive will be encrypted at rest in the database.
 */
@Getter
public enum RepositoryPropertyKey {

    // S3-specific properties
    S3_ACCESS_KEY("Access Key", true),
    S3_SECRET_KEY("Secret Key", true),
    S3_REGION("Region", false),

    // Azure-specific properties
    AZURE_ACCOUNT_NAME("Account Name", false),
    AZURE_ACCOUNT_KEY("Account Key", true),
    AZURE_ENDPOINT_SUFFIX("Endpoint Suffix", false),

    // SFTP-specific properties
    SFTP_PASSWORD_COMMAND("Password Command", false),
    SFTP_COMMAND("SFTP Command", false),

    // REST-specific properties
    REST_USERNAME("Username", false),
    REST_PASSWORD("Password", true),

    // Rclone-specific properties
    RCLONE_PROGRAM("Rclone Program", false),
    RCLONE_ARGS("Rclone Args", false);

    private final String displayName;
    private final boolean sensitive;

    RepositoryPropertyKey(String displayName, boolean sensitive) {
        this.displayName = displayName;
        this.sensitive = sensitive;
    }

}
