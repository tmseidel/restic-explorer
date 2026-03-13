package org.remus.resticexplorer.repository.web;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import org.remus.resticexplorer.repository.data.RepositoryType;

@Data
public class RepositoryForm {

    private Long id;

    @NotBlank(message = "{validation.name.required}")
    private String name;

    @NotNull(message = "{validation.type.required}")
    private RepositoryType type = RepositoryType.S3;

    @NotBlank(message = "{validation.url.required}")
    private String url;

    @NotBlank(message = "{validation.password.required}")
    private String repositoryPassword;

    private String s3AccessKey;
    private String s3SecretKey;
    private String s3Region;

    @NotNull(message = "{validation.scanInterval.required}")
    @Min(value = 1, message = "{validation.scanInterval.min}")
    private Integer scanIntervalMinutes = 60;

    private boolean enabled = true;

    private Long groupId;

    private String comment;
}
