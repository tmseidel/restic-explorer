package org.remus.resticexplorer.config.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class ResticCommandException extends RuntimeException {

    public ResticCommandException(String message) {
        super(message);
    }

    public ResticCommandException(String message, Throwable cause) {
        super(message, cause);
    }
}
