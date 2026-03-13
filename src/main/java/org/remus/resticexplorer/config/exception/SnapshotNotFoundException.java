package org.remus.resticexplorer.config.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class SnapshotNotFoundException extends RuntimeException {

    public SnapshotNotFoundException(String snapshotId) {
        super("Snapshot not found: " + snapshotId);
    }
}
