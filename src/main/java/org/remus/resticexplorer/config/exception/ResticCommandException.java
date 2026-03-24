package org.remus.resticexplorer.config.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class ResticCommandException extends RuntimeException {

    private final String stderr;

    public ResticCommandException(String message) {
        this(message, null, null);
    }

    public ResticCommandException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public ResticCommandException(String message, String stderr) {
        this(message, stderr, null);
    }

    public ResticCommandException(String message, String stderr, Throwable cause) {
        super(message, cause);
        this.stderr = stderr;
    }

    public String getStderr() {
        return stderr;
    }
}
