package org.remus.resticexplorer.repository.data;

/**
 * Enum representing configuration property keys for repository backends.
 * Each repository type defines which keys it requires.
 */
public enum RepositoryPropertyKey {

    // S3-specific properties
    S3_ACCESS_KEY("Access Key"),
    S3_SECRET_KEY("Secret Key"),
    S3_REGION("Region");

    private final String displayName;

    RepositoryPropertyKey(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
