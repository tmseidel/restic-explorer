package org.remus.resticexplorer.config.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(RepositoryNotFoundException.class)
    public ModelAndView handleRepositoryNotFound(RepositoryNotFoundException ex) {
        log.warn("Repository not found: {}", ex.getMessage());
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(HttpStatus.NOT_FOUND);
        mav.addObject("status", 404);
        mav.addObject("error", "Not Found");
        mav.addObject("message", ex.getMessage());
        return mav;
    }

    @ExceptionHandler(GroupNotFoundException.class)
    public ModelAndView handleGroupNotFound(GroupNotFoundException ex) {
        log.warn("Group not found: {}", ex.getMessage());
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(HttpStatus.NOT_FOUND);
        mav.addObject("status", 404);
        mav.addObject("error", "Not Found");
        mav.addObject("message", ex.getMessage());
        return mav;
    }

    @ExceptionHandler(SnapshotNotFoundException.class)
    public ModelAndView handleSnapshotNotFound(SnapshotNotFoundException ex) {
        log.warn("Snapshot not found: {}", ex.getMessage());
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(HttpStatus.NOT_FOUND);
        mav.addObject("status", 404);
        mav.addObject("error", "Not Found");
        mav.addObject("message", ex.getMessage());
        return mav;
    }

    @ExceptionHandler(ProviderNotFoundException.class)
    public ModelAndView handleProviderNotFound(ProviderNotFoundException ex) {
        log.error("Provider not found: {}", ex.getMessage());
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(HttpStatus.BAD_REQUEST);
        mav.addObject("status", 400);
        mav.addObject("error", "Bad Request");
        mav.addObject("message", ex.getMessage());
        return mav;
    }

    @ExceptionHandler(ResticCommandTimeoutException.class)
    public ModelAndView handleResticTimeout(ResticCommandTimeoutException ex) {
        log.error("Restic command timeout: {}", ex.getMessage());
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(HttpStatus.GATEWAY_TIMEOUT);
        mav.addObject("status", 504);
        mav.addObject("error", "Gateway Timeout");
        mav.addObject("message", ex.getMessage());
        return mav;
    }

    @ExceptionHandler(ResticCommandException.class)
    public ModelAndView handleResticCommand(ResticCommandException ex) {
        log.error("Restic command failed: {}", ex.getMessage());
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        mav.addObject("status", 500);
        mav.addObject("error", "Internal Server Error");
        mav.addObject("message", ex.getMessage());
        return mav;
    }
}
