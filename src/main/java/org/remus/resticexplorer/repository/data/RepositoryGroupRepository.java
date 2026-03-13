package org.remus.resticexplorer.repository.data;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RepositoryGroupRepository extends JpaRepository<RepositoryGroup, Long> {
    Optional<RepositoryGroup> findByName(String name);
}
