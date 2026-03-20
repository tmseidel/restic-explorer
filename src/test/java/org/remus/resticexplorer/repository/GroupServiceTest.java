package org.remus.resticexplorer.repository;

import org.junit.jupiter.api.Test;
import org.remus.resticexplorer.repository.data.RepositoryGroup;
import org.remus.resticexplorer.repository.data.RepositoryType;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GroupServiceTest {

    @Autowired
    private GroupService groupService;

    @Autowired
    private RepositoryService repositoryService;

    @Test
    void testCreateAndFindGroup() {
        RepositoryGroup group = new RepositoryGroup();
        group.setName("Production");
        group.setDescription("Production backup repositories");

        RepositoryGroup saved = groupService.save(group);
        assertNotNull(saved.getId());

        Optional<RepositoryGroup> found = groupService.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("Production", found.get().getName());
        assertEquals("Production backup repositories", found.get().getDescription());
    }

    @Test
    void testFindAllGroups() {
        RepositoryGroup group1 = new RepositoryGroup();
        group1.setName("Alpha");
        groupService.save(group1);

        RepositoryGroup group2 = new RepositoryGroup();
        group2.setName("Beta");
        groupService.save(group2);

        List<RepositoryGroup> groups = groupService.findAll();
        assertTrue(groups.size() >= 2);
        // Should be sorted by name
        assertTrue(groups.stream().anyMatch(g -> g.getName().equals("Alpha")));
        assertTrue(groups.stream().anyMatch(g -> g.getName().equals("Beta")));
    }

    @Test
    void testDeleteGroup() {
        RepositoryGroup group = new RepositoryGroup();
        group.setName("To Delete");
        RepositoryGroup saved = groupService.save(group);
        Long id = saved.getId();

        groupService.deleteById(id);
        assertFalse(groupService.findById(id).isPresent());
    }

    @Test
    void testAssignRepositoryToGroup() {
        RepositoryGroup group = new RepositoryGroup();
        group.setName("Servers");
        group = groupService.save(group);

        ResticRepository repo = new ResticRepository();
        repo.setName("Server Backup");
        repo.setType(RepositoryType.S3);
        repo.setUrl("s3:https://s3.amazonaws.com/test/restic");
        repo.setRepositoryPassword("secret");
        repo.setScanIntervalMinutes(60);
        repo.setEnabled(true);
        repo.setGroup(group);
        repo.setComment("Main server backup");

        ResticRepository saved = repositoryService.save(repo);
        assertNotNull(saved.getGroup());
        assertEquals("Servers", saved.getGroup().getName());
        assertEquals("Main server backup", saved.getComment());
    }

    @Test
    void testRepositoryCommentField() {
        ResticRepository repo = new ResticRepository();
        repo.setName("Commented Repo");
        repo.setType(RepositoryType.S3);
        repo.setUrl("s3:https://s3.amazonaws.com/test2/restic");
        repo.setRepositoryPassword("secret");
        repo.setScanIntervalMinutes(30);
        repo.setEnabled(true);
        repo.setComment("This is a test comment for the repository");

        ResticRepository saved = repositoryService.save(repo);
        Optional<ResticRepository> found = repositoryService.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("This is a test comment for the repository", found.get().getComment());
    }
}
