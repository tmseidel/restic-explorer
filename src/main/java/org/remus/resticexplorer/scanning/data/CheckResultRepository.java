package org.remus.resticexplorer.scanning.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CheckResultRepository extends JpaRepository<CheckResult, Long> {
    Optional<CheckResult> findTopByRepositoryIdOrderByCheckedAtDescIdDesc(Long repositoryId);
    List<CheckResult> findByRepositoryIdOrderByCheckedAtDescIdDesc(Long repositoryId);
}
