package org.remus.resticexplorer.admin;

import org.junit.jupiter.api.Test;
import org.remus.resticexplorer.admin.data.ErrorLogEntry;
import org.remus.resticexplorer.admin.data.ErrorLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ErrorLogServiceTest {

    @Autowired
    private ErrorLogService errorLogService;

    @Autowired
    private ErrorLogRepository errorLogRepository;

    @Test
    void testLogErrorSavesEntry() {
        RuntimeException cause = new RuntimeException("test error");
        errorLogService.logError(1L, "TestRepo", "SCAN", "Scan failed", cause);

        List<ErrorLogEntry> entries = errorLogRepository.findAll();
        assertEquals(1, entries.size());
        ErrorLogEntry entry = entries.get(0);
        assertEquals(1L, entry.getRepositoryId());
        assertEquals("TestRepo", entry.getRepositoryName());
        assertEquals("SCAN", entry.getAction());
        assertEquals("Scan failed", entry.getErrorMessage());
        assertNotNull(entry.getStackTrace());
        assertTrue(entry.getStackTrace().contains("RuntimeException"));
        assertNotNull(entry.getTimestamp());
    }

    @Test
    void testLogErrorWithNullMessage() {
        RuntimeException cause = new RuntimeException("from exception");
        errorLogService.logError(2L, "Repo2", "CHECK", null, cause);

        List<ErrorLogEntry> entries = errorLogRepository.findAll();
        assertEquals(1, entries.size());
        assertEquals("from exception", entries.get(0).getErrorMessage());
    }

    @Test
    void testLogErrorWithNullCause() {
        errorLogService.logError(3L, "Repo3", "SCAN", "Manual error", null);

        List<ErrorLogEntry> entries = errorLogRepository.findAll();
        assertEquals(1, entries.size());
        assertEquals("Manual error", entries.get(0).getErrorMessage());
        assertNull(entries.get(0).getStackTrace());
    }

    @Test
    void testFindErrorsByDateRange() {
        LocalDateTime now = LocalDateTime.now();

        // Entry inside range
        ErrorLogEntry inside = new ErrorLogEntry();
        inside.setRepositoryId(1L);
        inside.setRepositoryName("RepoA");
        inside.setAction("SCAN");
        inside.setErrorMessage("error inside");
        inside.setTimestamp(now.minusDays(1));
        errorLogRepository.save(inside);

        // Entry outside range (old)
        ErrorLogEntry outside = new ErrorLogEntry();
        outside.setRepositoryId(2L);
        outside.setRepositoryName("RepoB");
        outside.setAction("CHECK");
        outside.setErrorMessage("old error");
        outside.setTimestamp(now.minusDays(30));
        errorLogRepository.save(outside);

        LocalDateTime start = now.minusDays(7);
        LocalDateTime end = now;

        Page<ErrorLogEntry> page = errorLogService.findErrors(start, end,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "timestamp")));

        assertEquals(1, page.getTotalElements());
        assertEquals("error inside", page.getContent().get(0).getErrorMessage());
    }

    @Test
    void testFindErrorsPaginationAndSorting() {
        LocalDateTime base = LocalDateTime.now().minusDays(3);

        for (int i = 0; i < 5; i++) {
            ErrorLogEntry entry = new ErrorLogEntry();
            entry.setRepositoryId((long) i);
            entry.setRepositoryName("Repo" + i);
            entry.setAction("SCAN");
            entry.setErrorMessage("error " + i);
            entry.setTimestamp(base.plusHours(i));
            errorLogRepository.save(entry);
        }

        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();

        Page<ErrorLogEntry> page = errorLogService.findErrors(start, end,
                PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "timestamp")));

        assertEquals(5, page.getTotalElements());
        assertEquals(3, page.getContent().size());
        assertEquals(2, page.getTotalPages());
        // Verify descending order
        assertTrue(page.getContent().get(0).getTimestamp()
                .isAfter(page.getContent().get(1).getTimestamp()));
    }

    @Test
    void testCleanupOldEntriesRemovesOldEntries() {
        // Old entry (13 months ago)
        ErrorLogEntry old = new ErrorLogEntry();
        old.setRepositoryId(1L);
        old.setRepositoryName("OldRepo");
        old.setAction("SCAN");
        old.setErrorMessage("old");
        old.setTimestamp(LocalDateTime.now().minusMonths(13));
        errorLogRepository.save(old);

        // Recent entry
        ErrorLogEntry recent = new ErrorLogEntry();
        recent.setRepositoryId(2L);
        recent.setRepositoryName("NewRepo");
        recent.setAction("CHECK");
        recent.setErrorMessage("recent");
        recent.setTimestamp(LocalDateTime.now().minusDays(1));
        errorLogRepository.save(recent);

        assertEquals(2, errorLogRepository.count());

        errorLogService.cleanupOldEntries();

        assertEquals(1, errorLogRepository.count());
        assertEquals("recent", errorLogRepository.findAll().get(0).getErrorMessage());
    }
}
