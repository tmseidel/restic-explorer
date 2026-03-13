package org.remus.resticexplorer.repository.web;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class GroupForm {

    private Long id;

    @NotBlank(message = "Group name is required")
    private String name;

    private String description;
}
