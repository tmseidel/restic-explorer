package org.remus.resticexplorer.scanning;

import org.junit.jupiter.api.Test;
import org.remus.resticexplorer.repository.RepositoryService;
import org.remus.resticexplorer.repository.data.RepositoryType;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.remus.resticexplorer.scanning.data.CheckResult;
import org.remus.resticexplorer.scanning.data.CheckResultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IntegrityCheckServiceTest {

    @Autowired
    private CheckService checkService;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private CheckResultRepository checkResultRepository;

    @Test
    void testGetLastCheckResultEmpty() {
        ResticRepository repo = createTestRepo("Check Repo", 0);
        ResticRepository saved = repositoryService.save(repo);

        Optional<CheckResult> result = checkService.getLastCheckResult(saved.getId());
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetLastCheckResultReturnsLatest() throws InterruptedException {
        ResticRepository repo = createTestRepo("Check Repo", 60);
        ResticRepository saved = repositoryService.save(repo);

        CheckResult first = new CheckResult();
        first.setRepositoryId(saved.getId());
        first.setStatus(CheckResult.CheckStatus.SUCCESS);
        checkResultRepository.save(first);

        // Ensure the second check result gets a strictly later checkedAt timestamp
        Thread.sleep(10);

        CheckResult second = new CheckResult();
        second.setRepositoryId(saved.getId());
        second.setStatus(CheckResult.CheckStatus.FAILED);
        second.setMessage("Test failure");
        checkResultRepository.save(second);

        Optional<CheckResult> result = checkService.getLastCheckResult(saved.getId());
        assertTrue(result.isPresent());
        assertEquals(CheckResult.CheckStatus.FAILED, result.get().getStatus());
    }

    @Test
    void testCheckResultDoesNotReturnResultFromDifferentRepo() {
        ResticRepository repo1 = repositoryService.save(createTestRepo("Repo One", 60));
        ResticRepository repo2 = repositoryService.save(createTestRepo("Repo Two", 60));

        CheckResult check = new CheckResult();
        check.setRepositoryId(repo1.getId());
        check.setStatus(CheckResult.CheckStatus.SUCCESS);
        checkResultRepository.save(check);

        Optional<CheckResult> result = checkService.getLastCheckResult(repo2.getId());
        assertTrue(result.isEmpty());
    }

    @Test
    void testCheckIntervalDefaultIsZero() {
        ResticRepository repo = new ResticRepository();
        repo.setName("Default Repo");
        repo.setType(RepositoryType.S3);
        repo.setUrl("s3:https://s3.amazonaws.com/test-bucket/restic");
        repo.setRepositoryPassword("secret");
        repo.setEnabled(true);

        assertEquals(0, repo.getCheckIntervalMinutes());
    }

    @Test
    void testCheckIntervalPersistence() {
        ResticRepository repo = createTestRepo("Interval Repo", 120);
        ResticRepository saved = repositoryService.save(repo);

        Optional<ResticRepository> loaded = repositoryService.findById(saved.getId());
        assertTrue(loaded.isPresent());
        assertEquals(120, loaded.get().getCheckIntervalMinutes());
    }

    private ResticRepository createTestRepo(String name, int checkIntervalMinutes) {
        ResticRepository repo = new ResticRepository();
        repo.setName(name);
        repo.setType(RepositoryType.S3);
        repo.setUrl("s3:https://s3.amazonaws.com/test-bucket/restic");
        repo.setRepositoryPassword("secret");
        repo.setScanIntervalMinutes(60);
        repo.setCheckIntervalMinutes(checkIntervalMinutes);
        repo.setEnabled(true);
        return repo;
    }
}
