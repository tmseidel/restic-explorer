package org.remus.resticexplorer.admin.web;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class ChangePasswordForm {

    @NotBlank(message = "{validation.newPassword.required}")
    @Size(min = 8, message = "{validation.newPassword.size}")
    private String newPassword;

    @NotBlank(message = "{validation.confirmPassword.required}")
    private String confirmPassword;
}
