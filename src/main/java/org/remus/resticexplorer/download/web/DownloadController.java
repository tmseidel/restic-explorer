package org.remus.resticexplorer.download.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.remus.resticexplorer.repository.RepositoryService;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.remus.resticexplorer.restic.ResticCommandService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;

@Controller
@RequestMapping("/download")
@RequiredArgsConstructor
@Slf4j
public class DownloadController {

    private final RepositoryService repositoryService;
    private final ResticCommandService resticCommandService;

    @GetMapping("/{repositoryId}/{snapshotId}")
    public void downloadSnapshot(@PathVariable Long repositoryId,
                                  @PathVariable String snapshotId,
                                  HttpServletResponse response) throws Exception {
        ResticRepository repo = repositoryService.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));

        String filename = repo.getName().replaceAll("[^a-zA-Z0-9.-]", "_") + "_" + snapshotId + ".tar";

        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        try (InputStream is = resticCommandService.downloadSnapshot(repo, snapshotId)) {
            is.transferTo(response.getOutputStream());
            response.flushBuffer();
        } catch (Exception e) {
            log.error("Failed to download snapshot {} from repository {}: {}", snapshotId, repo.getName(), e.getMessage());
            throw e;
        }
    }
}
