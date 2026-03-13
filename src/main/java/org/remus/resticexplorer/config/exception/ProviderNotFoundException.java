package org.remus.resticexplorer.config.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ProviderNotFoundException extends RuntimeException {

    public ProviderNotFoundException(String type) {
        super("No provider found for repository type: " + type);
    }
}
