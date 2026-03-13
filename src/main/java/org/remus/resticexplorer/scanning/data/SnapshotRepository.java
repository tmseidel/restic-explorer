package org.remus.resticexplorer.scanning.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SnapshotRepository extends JpaRepository<Snapshot, Long> {
    List<Snapshot> findByRepositoryIdOrderBySnapshotTimeDesc(Long repositoryId);
    void deleteByRepositoryId(Long repositoryId);
    long countByRepositoryId(Long repositoryId);
}
