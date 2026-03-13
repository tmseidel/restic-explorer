package org.remus.resticexplorer.repository.web;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import org.remus.resticexplorer.repository.data.RepositoryType;

@Data
public class RepositoryForm {

    private Long id;

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Repository type is required")
    private RepositoryType type = RepositoryType.S3;

    @NotBlank(message = "URL is required")
    private String url;

    @NotBlank(message = "Repository password is required")
    private String repositoryPassword;

    private String s3AccessKey;
    private String s3SecretKey;
    private String s3Region;

    @NotNull(message = "Scan interval is required")
    @Min(value = 1, message = "Scan interval must be at least 1 minute")
    private Integer scanIntervalMinutes = 60;

    private boolean enabled = true;

    private Long groupId;

    private String comment;
}
