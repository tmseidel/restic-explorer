package org.remus.resticexplorer.scanning;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.remus.resticexplorer.config.exception.RepositoryNotFoundException;
import org.remus.resticexplorer.repository.RepositoryService;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.remus.resticexplorer.restic.ResticCommandService;
import org.remus.resticexplorer.scanning.data.CheckResult;
import org.remus.resticexplorer.scanning.data.CheckResultRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckService {

    private final RepositoryService repositoryService;
    private final CheckResultRepository checkResultRepository;
    private final ResticCommandService resticCommandService;

    @Scheduled(fixedDelayString = "${restic.check.check-interval:${restic.scan.check-interval:60000}}")
    public void scheduledCheck() {
        List<ResticRepository> repos = repositoryService.findAllEnabled();
        for (ResticRepository repo : repos) {
            if (shouldCheck(repo)) {
                checkRepository(repo.getId());
            }
        }
    }

    private boolean shouldCheck(ResticRepository repo) {
        if (repo.getCheckIntervalMinutes() == null || repo.getCheckIntervalMinutes() <= 0) {
            return false;
        }
        if (repo.getLastChecked() == null) {
            return true;
        }
        LocalDateTime nextCheck = repo.getLastChecked().plusMinutes(repo.getCheckIntervalMinutes());
        return LocalDateTime.now().isAfter(nextCheck);
    }

    @Transactional
    public CheckResult checkRepository(Long repositoryId) {
        ResticRepository repo = repositoryService.findById(repositoryId)
                .orElseThrow(() -> new RepositoryNotFoundException(repositoryId));

        CheckResult checkResult = new CheckResult();
        checkResult.setRepositoryId(repositoryId);
        checkResult.setStatus(CheckResult.CheckStatus.IN_PROGRESS);
        checkResultRepository.save(checkResult);

        try {
            String output = resticCommandService.checkRepository(repo);

            checkResult.setStatus(CheckResult.CheckStatus.SUCCESS);
            checkResult.setMessage(output);
            checkResultRepository.save(checkResult);

            repo.setLastChecked(LocalDateTime.now());
            repositoryService.save(repo);

            log.info("Integrity check passed for repository '{}'", repo.getName());
        } catch (Exception e) {
            log.error("Integrity check failed for repository '{}': {}", repo.getName(), e.getMessage(), e);
            checkResult.setStatus(CheckResult.CheckStatus.FAILED);
            checkResult.setMessage(e.getMessage());
            checkResultRepository.save(checkResult);
        }

        return checkResult;
    }

    public Optional<CheckResult> getLastCheckResult(Long repositoryId) {
        return checkResultRepository.findTopByRepositoryIdOrderByCheckedAtDesc(repositoryId);
    }
}
