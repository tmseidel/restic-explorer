package org.remus.resticexplorer.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.remus.resticexplorer.config.crypto.EncryptionService;
import org.remus.resticexplorer.repository.data.RepositoryPropertyKey;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.remus.resticexplorer.repository.data.ResticRepositoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RepositoryService {

    private final ResticRepositoryRepository repositoryRepository;
    private final EncryptionService encryptionService;
    private final EntityManager entityManager;

    public List<ResticRepository> findAll() {
        List<ResticRepository> repos = repositoryRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
        repos.forEach(this::decryptSensitiveProperties);
        return repos;
    }

    public Page<ResticRepository> findAll(Pageable pageable) {
        Page<ResticRepository> page = repositoryRepository.findAll(pageable);
        page.getContent().forEach(this::decryptSensitiveProperties);
        return page;
    }

    public List<ResticRepository> findAllEnabled() {
        List<ResticRepository> repos = repositoryRepository.findByEnabledTrue();
        repos.forEach(this::decryptSensitiveProperties);
        return repos;
    }

    public Optional<ResticRepository> findById(Long id) {
        return repositoryRepository.findById(id)
                .map(this::decryptSensitiveProperties);
    }

    @Transactional
    public ResticRepository save(ResticRepository repository) {
        encryptSensitiveProperties(repository);
        ResticRepository saved = repositoryRepository.saveAndFlush(repository);
        // Detach so that decrypting in-memory doesn't trigger dirty-check writes
        entityManager.detach(saved);
        decryptSensitiveProperties(saved);
        return saved;
    }

    @Transactional
    public void deleteById(Long id) {
        repositoryRepository.deleteById(id);
    }

    private void encryptSensitiveProperties(ResticRepository repo) {
        Map<RepositoryPropertyKey, String> props = repo.getProperties();
        for (RepositoryPropertyKey key : RepositoryPropertyKey.values()) {
            if (key.isSensitive() && props.containsKey(key)) {
                String value = props.get(key);
                if (value != null && !value.isBlank()) {
                    props.put(key, encryptionService.encrypt(value));
                }
            }
        }
    }

    private ResticRepository decryptSensitiveProperties(ResticRepository repo) {
        Map<RepositoryPropertyKey, String> props = repo.getProperties();
        for (RepositoryPropertyKey key : RepositoryPropertyKey.values()) {
            if (key.isSensitive() && props.containsKey(key)) {
                String value = props.get(key);
                if (value != null && !value.isBlank()) {
                    props.put(key, encryptionService.decrypt(value));
                }
            }
        }
        return repo;
    }
}
