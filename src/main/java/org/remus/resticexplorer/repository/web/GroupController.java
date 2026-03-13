package org.remus.resticexplorer.repository.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.remus.resticexplorer.repository.GroupService;
import org.remus.resticexplorer.repository.data.RepositoryGroup;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("groups", groupService.findAll());
        return "group/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("groupForm", new GroupForm());
        return "group/form";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        RepositoryGroup group = groupService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + id));
        GroupForm form = new GroupForm();
        form.setId(group.getId());
        form.setName(group.getName());
        form.setDescription(group.getDescription());
        model.addAttribute("groupForm", form);
        return "group/form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute GroupForm form,
                       BindingResult result, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "group/form";
        }
        RepositoryGroup group;
        if (form.getId() != null) {
            group = groupService.findById(form.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Group not found: " + form.getId()));
        } else {
            group = new RepositoryGroup();
        }
        group.setName(form.getName());
        group.setDescription(form.getDescription());
        groupService.save(group);
        redirectAttributes.addFlashAttribute("successMessage", "Group saved successfully");
        return "redirect:/groups";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        groupService.deleteById(id);
        redirectAttributes.addFlashAttribute("successMessage", "Group deleted successfully");
        return "redirect:/groups";
    }
}
