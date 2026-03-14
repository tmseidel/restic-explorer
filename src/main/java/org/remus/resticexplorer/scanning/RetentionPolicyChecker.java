package org.remus.resticexplorer.scanning;

import org.remus.resticexplorer.repository.data.ResticRepository;
import org.remus.resticexplorer.scanning.data.Snapshot;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stateless service that evaluates cached snapshots against a repository's configured retention policy.
 * Operates purely on already-fetched data — never calls the restic CLI.
 */
@Service
public class RetentionPolicyChecker {

    /**
     * Check whether the given snapshots satisfy the retention policy configured on the repository.
     *
     * @param repository    the repository with optional retention policy fields
     * @param snapshots     the cached snapshots for that repository
     * @param referenceDate the date to treat as "today" (use {@code LocalDate.now()} in production)
     * @return a {@link RetentionPolicyResult} describing pass/fail and any violation messages
     */
    public RetentionPolicyResult check(ResticRepository repository, List<Snapshot> snapshots, LocalDate referenceDate) {
        Integer keepDaily = repository.getKeepDaily();
        Integer keepWeekly = repository.getKeepWeekly();
        Integer keepMonthly = repository.getKeepMonthly();
        Integer keepYearly = repository.getKeepYearly();
        Integer keepLast = repository.getKeepLast();

        // If no policy is configured at all, return fulfilled with no violations
        boolean noPolicyConfigured = isZeroOrNull(keepDaily)
                && isZeroOrNull(keepWeekly)
                && isZeroOrNull(keepMonthly)
                && isZeroOrNull(keepYearly)
                && isZeroOrNull(keepLast);

        if (noPolicyConfigured) {
            return RetentionPolicyResult.ok();
        }

        RetentionPolicyResult result = new RetentionPolicyResult();
        result.setFulfilled(true);

        // Build a set of dates that have at least one snapshot (null snapshotTime snapshots are skipped for
        // time-based rules but still count toward keepLast)
        Set<LocalDate> coveredDates = snapshots.stream()
                .filter(s -> s.getSnapshotTime() != null)
                .map(s -> s.getSnapshotTime().toLocalDate())
                .collect(Collectors.toSet());

        long totalCount = snapshots.size();

        // --- keepLast ---
        if (!isZeroOrNull(keepLast)) {
            if (totalCount < keepLast) {
                result.setFulfilled(false);
                result.getViolations().add(
                        "keepLast: Expected at least " + keepLast + " snapshots, found " + totalCount);
            }
        }

        // --- keepDaily ---
        if (!isZeroOrNull(keepDaily)) {
            for (int i = 0; i < keepDaily; i++) {
                LocalDate day = referenceDate.minusDays(i);
                if (!coveredDates.contains(day)) {
                    result.setFulfilled(false);
                    result.getViolations().add("keepDaily: Missing backup for " + day);
                }
            }
        }

        // --- keepWeekly ---
        if (!isZeroOrNull(keepWeekly)) {
            WeekFields weekFields = WeekFields.of(Locale.getDefault());
            Set<String> coveredWeeks = coveredDates.stream()
                    .map(d -> d.getYear() + "-W" + String.format("%02d", d.get(weekFields.weekOfWeekBasedYear())))
                    .collect(Collectors.toSet());

            for (int i = 0; i < keepWeekly; i++) {
                LocalDate weekRef = referenceDate.minusWeeks(i);
                String weekKey = weekRef.getYear() + "-W" + String.format("%02d", weekRef.get(weekFields.weekOfWeekBasedYear()));
                if (!coveredWeeks.contains(weekKey)) {
                    result.setFulfilled(false);
                    result.getViolations().add("keepWeekly: No backup in week " + weekKey);
                }
            }
        }

        // --- keepMonthly ---
        if (!isZeroOrNull(keepMonthly)) {
            Set<String> coveredMonths = coveredDates.stream()
                    .map(d -> d.getYear() + "-" + String.format("%02d", d.getMonthValue()))
                    .collect(Collectors.toSet());

            for (int i = 0; i < keepMonthly; i++) {
                LocalDate monthRef = referenceDate.minusMonths(i);
                String monthKey = monthRef.getYear() + "-" + String.format("%02d", monthRef.getMonthValue());
                if (!coveredMonths.contains(monthKey)) {
                    result.setFulfilled(false);
                    result.getViolations().add("keepMonthly: No backup in month " + monthKey);
                }
            }
        }

        // --- keepYearly ---
        if (!isZeroOrNull(keepYearly)) {
            Set<Integer> coveredYears = coveredDates.stream()
                    .map(LocalDate::getYear)
                    .collect(Collectors.toSet());

            for (int i = 0; i < keepYearly; i++) {
                int year = referenceDate.minusYears(i).getYear();
                if (!coveredYears.contains(year)) {
                    result.setFulfilled(false);
                    result.getViolations().add("keepYearly: No backup in year " + year);
                }
            }
        }

        return result;
    }

    private boolean isZeroOrNull(Integer value) {
        return value == null || value == 0;
    }
}
