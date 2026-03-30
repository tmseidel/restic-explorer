package org.remus.resticexplorer.scanning.web.dto;

import java.util.List;
import java.util.Map;

public record StatusResponse(
        String overallStatus,
        Map<Long, RepositoryStatusDto> repositories,
        List<ActiveJobDto> activeJobs
) {}
