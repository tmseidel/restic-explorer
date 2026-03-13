package org.remus.resticexplorer.admin.web;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class SetupForm {

    @NotBlank(message = "{validation.password.setup.required}")
    @Size(min = 8, message = "{validation.password.setup.size}")
    private String password;

    @NotBlank(message = "{validation.confirmPassword.setup.required}")
    private String confirmPassword;
}
