package org.remus.resticexplorer.repository.data;

public enum RepositoryType {
    S3("Amazon S3 / S3-Compatible"),
    AZURE("Microsoft Azure Blob Storage"),
    SFTP("SFTP");

    private final String displayName;

    RepositoryType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
