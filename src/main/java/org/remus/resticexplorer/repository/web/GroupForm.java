package org.remus.resticexplorer.repository.web;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GroupForm {

    private Long id;

    @NotBlank(message = "{validation.groupName.required}")
    private String name;

    private String description;
}
