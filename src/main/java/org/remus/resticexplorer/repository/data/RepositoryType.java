package org.remus.resticexplorer.repository.data;

public enum RepositoryType {
    S3("Amazon S3 / S3-Compatible");

    private final String displayName;

    RepositoryType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
