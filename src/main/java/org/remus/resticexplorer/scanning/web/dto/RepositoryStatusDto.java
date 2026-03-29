package org.remus.resticexplorer.scanning.web.dto;

public record RepositoryStatusDto(
        String scanStatus,
        String checkStatus,
        long snapshotCount,
        String lastScanned
) {}
