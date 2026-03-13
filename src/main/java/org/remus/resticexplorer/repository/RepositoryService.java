package org.remus.resticexplorer.repository;

import lombok.RequiredArgsConstructor;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.remus.resticexplorer.repository.data.ResticRepositoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RepositoryService {

    private final ResticRepositoryRepository repositoryRepository;

    public List<ResticRepository> findAll() {
        return repositoryRepository.findAll();
    }

    public List<ResticRepository> findAllEnabled() {
        return repositoryRepository.findByEnabledTrue();
    }

    public Optional<ResticRepository> findById(Long id) {
        return repositoryRepository.findById(id);
    }

    @Transactional
    public ResticRepository save(ResticRepository repository) {
        return repositoryRepository.save(repository);
    }

    @Transactional
    public void deleteById(Long id) {
        repositoryRepository.deleteById(id);
    }
}
