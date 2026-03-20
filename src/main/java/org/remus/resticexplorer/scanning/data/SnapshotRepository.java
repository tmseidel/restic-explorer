package org.remus.resticexplorer.scanning.data;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface SnapshotRepository extends JpaRepository<Snapshot, Long> {
    List<Snapshot> findByRepositoryIdOrderBySnapshotTimeDesc(Long repositoryId);
    Page<Snapshot> findByRepositoryId(Long repositoryId, Pageable pageable);
    Optional<Snapshot> findByRepositoryIdAndSnapshotId(Long repositoryId, String snapshotId);
    @Modifying
    @Transactional
    void deleteByRepositoryId(Long repositoryId);
    long countByRepositoryId(Long repositoryId);
}
