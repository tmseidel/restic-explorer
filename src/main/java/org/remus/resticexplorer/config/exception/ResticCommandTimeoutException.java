package org.remus.resticexplorer.config.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
public class ResticCommandTimeoutException extends ResticCommandException {

    public ResticCommandTimeoutException(int timeoutSeconds) {
        super("Restic command timed out after " + timeoutSeconds + " seconds");
    }
}
