package org.remus.resticexplorer.scanning.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ScanResultRepository extends JpaRepository<ScanResult, Long> {
    Optional<ScanResult> findTopByRepositoryIdOrderByScannedAtDesc(Long repositoryId);
    List<ScanResult> findByRepositoryIdOrderByScannedAtDesc(Long repositoryId);
}
