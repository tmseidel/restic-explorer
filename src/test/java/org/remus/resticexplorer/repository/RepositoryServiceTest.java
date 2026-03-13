package org.remus.resticexplorer.repository;

import org.junit.jupiter.api.Test;
import org.remus.resticexplorer.repository.data.RepositoryType;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RepositoryServiceTest {

    @Autowired
    private RepositoryService repositoryService;

    @Test
    void testCreateAndFindRepository() {
        ResticRepository repo = new ResticRepository();
        repo.setName("Test Repo");
        repo.setType(RepositoryType.S3);
        repo.setUrl("s3:https://s3.amazonaws.com/test-bucket/restic");
        repo.setRepositoryPassword("secret");
        repo.setScanIntervalMinutes(60);
        repo.setEnabled(true);

        ResticRepository saved = repositoryService.save(repo);
        assertNotNull(saved.getId());

        Optional<ResticRepository> found = repositoryService.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("Test Repo", found.get().getName());
    }

    @Test
    void testFindAllEnabled() {
        ResticRepository repo1 = new ResticRepository();
        repo1.setName("Enabled Repo");
        repo1.setType(RepositoryType.S3);
        repo1.setUrl("s3:https://s3.amazonaws.com/bucket1/restic");
        repo1.setRepositoryPassword("secret");
        repo1.setScanIntervalMinutes(60);
        repo1.setEnabled(true);
        repositoryService.save(repo1);

        ResticRepository repo2 = new ResticRepository();
        repo2.setName("Disabled Repo");
        repo2.setType(RepositoryType.S3);
        repo2.setUrl("s3:https://s3.amazonaws.com/bucket2/restic");
        repo2.setRepositoryPassword("secret");
        repo2.setScanIntervalMinutes(60);
        repo2.setEnabled(false);
        repositoryService.save(repo2);

        List<ResticRepository> enabled = repositoryService.findAllEnabled();
        assertEquals(1, enabled.size());
        assertEquals("Enabled Repo", enabled.get(0).getName());
    }

    @Test
    void testDeleteRepository() {
        ResticRepository repo = new ResticRepository();
        repo.setName("To Delete");
        repo.setType(RepositoryType.S3);
        repo.setUrl("s3:https://s3.amazonaws.com/delete-bucket/restic");
        repo.setRepositoryPassword("secret");
        repo.setScanIntervalMinutes(60);
        repo.setEnabled(true);

        ResticRepository saved = repositoryService.save(repo);
        Long id = saved.getId();

        repositoryService.deleteById(id);
        assertFalse(repositoryService.findById(id).isPresent());
    }
}
