package org.remus.resticexplorer.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.remus.resticexplorer.admin.data.ErrorLogEntry;
import org.remus.resticexplorer.admin.data.ErrorLogRepository;
import org.remus.resticexplorer.config.exception.ResticCommandException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ErrorLogService {

    private final ErrorLogRepository errorLogRepository;

    @Transactional
    public void logError(Long repositoryId, String repositoryName, String action,
                         String message, Throwable cause) {
        ErrorLogEntry entry = new ErrorLogEntry();
        entry.setRepositoryId(repositoryId);
        entry.setRepositoryName(repositoryName);
        entry.setAction(action);
        entry.setErrorMessage(message != null ? message
                : (cause != null ? cause.getMessage() : "Unknown error"));
        if (cause != null) {
            StringWriter sw = new StringWriter();
            if (cause instanceof ResticCommandException rce
                    && rce.getStderr() != null && !rce.getStderr().isBlank()) {
                sw.write("stderr:\n");
                sw.write(rce.getStderr());
                sw.write("\n");
            }
            cause.printStackTrace(new PrintWriter(sw));
            entry.setStackTrace(sw.toString());
        }
        errorLogRepository.save(entry);
    }

    public Page<ErrorLogEntry> findErrors(LocalDateTime start, LocalDateTime end, Pageable pageable) {
        return errorLogRepository.findByTimestampBetween(start, end, pageable);
    }

    @Transactional
    public void deleteAll() {
        errorLogRepository.deleteAllInBatch();
        log.info("All error log entries deleted by admin");
    }

    // Runs daily at 03:00 to remove entries older than 12 months
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOldEntries() {
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(12);
        long count = errorLogRepository.deleteByTimestampBefore(cutoff);
        if (count > 0) {
            log.info("Deleted {} error log entries older than 12 months", count);
        }
    }
}
