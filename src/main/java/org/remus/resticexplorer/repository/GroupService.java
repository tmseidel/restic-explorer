package org.remus.resticexplorer.repository;

import lombok.RequiredArgsConstructor;
import org.remus.resticexplorer.repository.data.RepositoryGroup;
import org.remus.resticexplorer.repository.data.RepositoryGroupRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final RepositoryGroupRepository groupRepository;

    public List<RepositoryGroup> findAll() {
        return groupRepository.findAll(Sort.by("name"));
    }

    public Page<RepositoryGroup> findAll(Pageable pageable) {
        return groupRepository.findAll(pageable);
    }

    public Optional<RepositoryGroup> findById(Long id) {
        return groupRepository.findById(id);
    }

    @Transactional
    public RepositoryGroup save(RepositoryGroup group) {
        return groupRepository.save(group);
    }

    @Transactional
    public void deleteById(Long id) {
        groupRepository.deleteById(id);
    }
}
