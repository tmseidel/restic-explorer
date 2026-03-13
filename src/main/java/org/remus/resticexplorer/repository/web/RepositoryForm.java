package org.remus.resticexplorer.repository.web;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import org.remus.resticexplorer.repository.data.RepositoryType;

@Data
public class RepositoryForm {

    /**
     * Sentinel value used for password/secret fields in edit mode.
     * If the form is submitted with this value, the existing DB value is kept.
     */
    public static final String SENSITIVE_PLACEHOLDER = "••••••••";

    private Long id;

    @NotBlank(message = "{validation.name.required}")
    private String name;

    @NotNull(message = "{validation.type.required}")
    private RepositoryType type = RepositoryType.S3;

    @NotBlank(message = "{validation.url.required}")
    private String url;

    // Validated manually in controller: required only for create, optional for edit
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

    /**
     * Returns true if the given value is the sentinel placeholder (i.e. unchanged).
     */
    public static boolean isUnchanged(String value) {
        return SENSITIVE_PLACEHOLDER.equals(value);
    }
}
