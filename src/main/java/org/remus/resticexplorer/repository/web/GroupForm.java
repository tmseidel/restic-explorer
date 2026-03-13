package org.remus.resticexplorer.repository.web;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class GroupForm {

    private Long id;

    @NotBlank(message = "{validation.groupName.required}")
    private String name;

    private String description;
}
