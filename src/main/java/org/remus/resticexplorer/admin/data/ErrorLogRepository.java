package org.remus.resticexplorer.admin.data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface ErrorLogRepository extends JpaRepository<ErrorLogEntry, Long> {

    Page<ErrorLogEntry> findByTimestampBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    long deleteByTimestampBefore(LocalDateTime cutoff);
}
