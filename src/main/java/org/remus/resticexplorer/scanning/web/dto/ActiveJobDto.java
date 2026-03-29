package org.remus.resticexplorer.scanning.web.dto;

public record ActiveJobDto(
        Long repositoryId,
        String repositoryName,
        String type
) {}
