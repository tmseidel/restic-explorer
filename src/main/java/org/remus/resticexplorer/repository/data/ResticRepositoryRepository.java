package org.remus.resticexplorer.repository.data;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ResticRepositoryRepository extends JpaRepository<ResticRepository, Long> {
    List<ResticRepository> findByEnabledTrue();
    List<ResticRepository> findByGroupIsNullOrderByNameAsc();
    List<ResticRepository> findByGroupIdOrderByNameAsc(Long groupId);
    List<ResticRepository> findAllByOrderByGroupNameAscNameAsc();
}
