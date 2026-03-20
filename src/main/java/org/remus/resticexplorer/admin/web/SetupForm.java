package org.remus.resticexplorer.admin.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SetupForm {

    @NotBlank(message = "{validation.password.setup.required}")
    @Size(min = 8, message = "{validation.password.setup.size}")
    private String password;

    @NotBlank(message = "{validation.confirmPassword.setup.required}")
    private String confirmPassword;
}
