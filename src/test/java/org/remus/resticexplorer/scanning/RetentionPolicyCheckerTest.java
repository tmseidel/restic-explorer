package org.remus.resticexplorer.scanning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.remus.resticexplorer.repository.data.RepositoryType;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.remus.resticexplorer.scanning.data.Snapshot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class RetentionPolicyCheckerTest {

    private RetentionPolicyChecker checker;
    private static final LocalDate REF = LocalDate.of(2026, 3, 14);

    @BeforeEach
    void setUp() {
        checker = new RetentionPolicyChecker();
    }

    // =========================================================================
    // keepLast tests
    // =========================================================================

    @Test
    void testKeepLast_Sufficient() {
        ResticRepository repo = repoWithKeepLast(10);
        List<Snapshot> snapshots = nSnapshots(10);
        RetentionPolicyResult result = checker.check(repo, snapshots, REF);
        assertTrue(result.isFulfilled());
        assertTrue(result.getViolations().isEmpty());
    }

    @Test
    void testKeepLast_Insufficient() {
        ResticRepository repo = repoWithKeepLast(10);
        List<Snapshot> snapshots = nSnapshots(7);
        RetentionPolicyResult result = checker.check(repo, snapshots, REF);
        assertFalse(result.isFulfilled());
        assertEquals(1, result.getViolations().size());
        assertTrue(result.getViolations().get(0).contains("10"));
        assertTrue(result.getViolations().get(0).contains("7"));
    }

    @Test
    void testKeepLast_Zero() {
        ResticRepository repo = repoWithKeepLast(0);
        RetentionPolicyResult result = checker.check(repo, List.of(), REF);
        assertTrue(result.isFulfilled());
        assertTrue(result.getViolations().isEmpty());
    }

    @Test
    void testKeepLast_ExactlyMet() {
        ResticRepository repo = repoWithKeepLast(5);
        List<Snapshot> snapshots = nSnapshots(5);
        RetentionPolicyResult result = checker.check(repo, snapshots, REF);
        assertTrue(result.isFulfilled());
    }

    // =========================================================================
    // keepDaily tests
    // =========================================================================

    @Test
    void testKeepDaily_AllDaysCovered() {
        ResticRepository repo = repoWithKeepDaily(7);
        List<Snapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            snapshots.add(snapshotAtDate(REF.minusDays(i)));
        }
        RetentionPolicyResult result = checker.check(repo, snapshots, REF);
        assertTrue(result.isFulfilled());
        assertTrue(result.getViolations().isEmpty());
    }

    @Test
    void testKeepDaily_MissingOneDay() {
        ResticRepository repo = repoWithKeepDaily(7);
        List<Snapshot> snapshots = new ArrayList<>();
        // Add all days except day 3 (REF - 3)
        for (int i = 0; i < 7; i++) {
            if (i != 3) {
                snapshots.add(snapshotAtDate(REF.minusDays(i)));
            }
        }
        RetentionPolicyResult result = checker.check(repo, snapshots, REF);
        assertFalse(result.isFulfilled());
        assertEquals(1, result.getViolations().size());
        assertTrue(result.getViolations().get(0).contains(REF.minusDays(3).toString()));
    }

    @Test
    void testKeepDaily_MultipleSnapshotsPerDay() {
        ResticRepository repo = repoWithKeepDaily(3);
        List<Snapshot> snapshots = new ArrayList<>();
        // Two snapshots for REF and one each for REF-1 and REF-2
        snapshots.add(snapshotAtDate(REF));
        snapshots.add(snapshotAtDate(REF));
        snapshots.add(snapshotAtDate(REF.minusDays(1)));
        snapshots.add(snapshotAtDate(REF.minusDays(2)));
        RetentionPolicyResult result = checker.check(repo, snapshots, REF);
        assertTrue(result.isFulfilled());
    }

    @Test
    void testKeepDaily_AllMissing() {
        ResticRepository repo = repoWithKeepDaily(7);
        RetentionPolicyResult result = checker.check(repo, List.of(), REF);
        assertFalse(result.isFulfilled());
        assertEquals(7, result.getViolations().size());
    }

    // =========================================================================
    // keepWeekly tests
    // =========================================================================

    @Test
    void testKeepWeekly_AllWeeksCovered() {
        ResticRepository repo = repoWithKeepWeekly(4);
        List<Snapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            // One snapshot per week (use Wednesday of each week)
            snapshots.add(snapshotAtDate(REF.minusWeeks(i)));
        }
        RetentionPolicyResult result = checker.check(repo, snapshots, REF);
        assertTrue(result.isFulfilled());
        assertTrue(result.getViolations().isEmpty());
    }

    @Test
    void testKeepWeekly_MissingOneWeek() {
        ResticRepository repo = repoWithKeepWeekly(4);
        List<Snapshot> snapshots = new ArrayList<>();
        // Cover only 3 of 4 weeks (skip week 2 ago)
        snapshots.add(snapshotAtDate(REF));
        snapshots.add(snapshotAtDate(REF.minusWeeks(1)));
        snapshots.add(snapshotAtDate(REF.minusWeeks(3)));
        RetentionPolicyResult result = checker.check(repo, snapshots, REF);
        assertFalse(result.isFulfilled());
        assertEquals(1, result.getViolations().size());
        assertTrue(result.getViolations().get(0).contains("keepWeekly"));
    }

    @Test
    void testKeepWeekly_SnapshotOnEdgeOfWeek() {
        // A snapshot on the last day of a week should count for that week
        ResticRepository repo = repoWithKeepWeekly(1);
        WeekFields wf = WeekFields.of(Locale.getDefault());
        // Find the first day of the current week
        LocalDate startOfWeek = REF.with(wf.dayOfWeek(), 1);
        List<Snapshot> snapshots = List.of(snapshotAtDate(startOfWeek));
        RetentionPolicyResult result = checker.check(repo, snapshots, REF);
        assertTrue(result.isFulfilled());
    }

    // =========================================================================
    // keepMonthly tests
    // =========================================================================

    @Test
    void testKeepMonthly_AllMonthsCovered() {
        ResticRepository repo = repoWithKeepMonthly(12);
        List<Snapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            snapshots.add(snapshotAtDate(REF.minusMonths(i)));
        }
        RetentionPolicyResult result = checker.check(repo, snapshots, REF);
        assertTrue(result.isFulfilled());
        assertTrue(result.getViolations().isEmpty());
    }

    @Test
    void testKeepMonthly_MissingMonth() {
        ResticRepository repo = repoWithKeepMonthly(3);
        List<Snapshot> snapshots = new ArrayList<>();
        // Cover current month and 2 months ago, but skip 1 month ago
        snapshots.add(snapshotAtDate(REF));
        snapshots.add(snapshotAtDate(REF.minusMonths(2)));
        RetentionPolicyResult result = checker.check(repo, snapshots, REF);
        assertFalse(result.isFulfilled());
        assertEquals(1, result.getViolations().size());
        assertTrue(result.getViolations().get(0).contains("keepMonthly"));
    }

    // =========================================================================
    // keepYearly tests
    // =========================================================================

    @Test
    void testKeepYearly_AllYearsCovered() {
        ResticRepository repo = repoWithKeepYearly(2);
        List<Snapshot> snapshots = new ArrayList<>();
        snapshots.add(snapshotAtDate(REF));
        snapshots.add(snapshotAtDate(REF.minusYears(1)));
        RetentionPolicyResult result = checker.check(repo, snapshots, REF);
        assertTrue(result.isFulfilled());
    }

    @Test
    void testKeepYearly_MissingYear() {
        ResticRepository repo = repoWithKeepYearly(2);
        // Only have a snapshot for current year
        List<Snapshot> snapshots = List.of(snapshotAtDate(REF));
        RetentionPolicyResult result = checker.check(repo, snapshots, REF);
        assertFalse(result.isFulfilled());
        assertEquals(1, result.getViolations().size());
        assertTrue(result.getViolations().get(0).contains("keepYearly"));
        assertTrue(result.getViolations().get(0).contains(String.valueOf(REF.minusYears(1).getYear())));
    }

    // =========================================================================
    // Combined / edge cases
    // =========================================================================

    @Test
    void testNoPolicyConfigured() {
        ResticRepository repo = newRepo();
        RetentionPolicyResult result = checker.check(repo, List.of(), REF);
        assertTrue(result.isFulfilled());
        assertTrue(result.getViolations().isEmpty());
    }

    @Test
    void testAllZeros() {
        ResticRepository repo = newRepo();
        repo.setKeepDaily(0);
        repo.setKeepWeekly(0);
        repo.setKeepMonthly(0);
        repo.setKeepYearly(0);
        repo.setKeepLast(0);
        RetentionPolicyResult result = checker.check(repo, List.of(), REF);
        assertTrue(result.isFulfilled());
        assertTrue(result.getViolations().isEmpty());
    }

    @Test
    void testMultipleRules_AllPass() {
        ResticRepository repo = newRepo();
        repo.setKeepDaily(3);
        repo.setKeepLast(5);
        List<Snapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            snapshots.add(snapshotAtDate(REF.minusDays(i)));
        }
        // Add extra snapshots so keepLast=5 is satisfied (already have 3, add 2 more)
        snapshots.add(snapshotAtDate(REF.minusDays(4)));
        snapshots.add(snapshotAtDate(REF.minusDays(5)));
        RetentionPolicyResult result = checker.check(repo, snapshots, REF);
        assertTrue(result.isFulfilled());
        assertTrue(result.getViolations().isEmpty());
    }

    @Test
    void testMultipleRules_OneFails() {
        ResticRepository repo = newRepo();
        repo.setKeepDaily(3);
        repo.setKeepLast(10);
        // 3 days covered, but only 3 snapshots total (keepLast=10 fails)
        List<Snapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            snapshots.add(snapshotAtDate(REF.minusDays(i)));
        }
        RetentionPolicyResult result = checker.check(repo, snapshots, REF);
        assertFalse(result.isFulfilled());
        assertEquals(1, result.getViolations().size());
        assertTrue(result.getViolations().get(0).contains("keepLast"));
    }

    @Test
    void testEmptySnapshotList() {
        ResticRepository repo = repoWithKeepDaily(1);
        RetentionPolicyResult result = checker.check(repo, List.of(), REF);
        assertFalse(result.isFulfilled());
        assertFalse(result.getViolations().isEmpty());
    }

    @Test
    void testSnapshotsWithNullTime_SkippedForTimeBased_ButCountForKeepLast() {
        ResticRepository repo = newRepo();
        repo.setKeepDaily(1);
        repo.setKeepLast(2);

        // One snapshot with null time, one with valid time today
        Snapshot withNullTime = new Snapshot();
        withNullTime.setRepositoryId(1L);
        withNullTime.setSnapshotId("null-time");
        withNullTime.setSnapshotTime(null);

        Snapshot withTime = snapshotAtDate(REF);

        List<Snapshot> snapshots = List.of(withNullTime, withTime);
        RetentionPolicyResult result = checker.check(repo, snapshots, REF);

        // keepDaily should pass (today is covered)
        // keepLast=2 should pass (2 snapshots including null-time one)
        assertTrue(result.isFulfilled(), "Violations: " + result.getViolations());
    }

    @Test
    void testSnapshotsWithNullTime_KeepLastFails() {
        ResticRepository repo = repoWithKeepLast(3);

        // Only 1 snapshot (null time still counts for keepLast)
        Snapshot withNullTime = new Snapshot();
        withNullTime.setRepositoryId(1L);
        withNullTime.setSnapshotId("null-time");
        withNullTime.setSnapshotTime(null);

        RetentionPolicyResult result = checker.check(repo, List.of(withNullTime), REF);
        assertFalse(result.isFulfilled());
        assertTrue(result.getViolations().get(0).contains("keepLast"));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private ResticRepository newRepo() {
        ResticRepository repo = new ResticRepository();
        repo.setId(1L);
        repo.setName("Test Repo");
        repo.setType(RepositoryType.S3);
        repo.setUrl("s3://test");
        repo.setRepositoryPassword("secret");
        repo.setScanIntervalMinutes(60);
        return repo;
    }

    private ResticRepository repoWithKeepDaily(int n) {
        ResticRepository repo = newRepo();
        repo.setKeepDaily(n);
        return repo;
    }

    private ResticRepository repoWithKeepWeekly(int n) {
        ResticRepository repo = newRepo();
        repo.setKeepWeekly(n);
        return repo;
    }

    private ResticRepository repoWithKeepMonthly(int n) {
        ResticRepository repo = newRepo();
        repo.setKeepMonthly(n);
        return repo;
    }

    private ResticRepository repoWithKeepYearly(int n) {
        ResticRepository repo = newRepo();
        repo.setKeepYearly(n);
        return repo;
    }

    private ResticRepository repoWithKeepLast(int n) {
        ResticRepository repo = newRepo();
        repo.setKeepLast(n);
        return repo;
    }

    private Snapshot snapshotAtDate(LocalDate date) {
        Snapshot snapshot = new Snapshot();
        snapshot.setRepositoryId(1L);
        snapshot.setSnapshotId("snap-" + date);
        snapshot.setSnapshotTime(date.atTime(12, 0));
        return snapshot;
    }

    private List<Snapshot> nSnapshots(int n) {
        List<Snapshot> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Snapshot s = new Snapshot();
            s.setRepositoryId(1L);
            s.setSnapshotId("snap-" + i);
            s.setSnapshotTime(LocalDateTime.now().minusDays(i));
            list.add(s);
        }
        return list;
    }
}
