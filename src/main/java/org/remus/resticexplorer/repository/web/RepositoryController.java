package org.remus.resticexplorer.repository.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.remus.resticexplorer.config.exception.RepositoryNotFoundException;
import org.remus.resticexplorer.repository.GroupService;
import org.remus.resticexplorer.repository.RepositoryService;
import org.remus.resticexplorer.repository.data.RepositoryPropertyKey;
import org.remus.resticexplorer.repository.data.RepositoryType;
import org.remus.resticexplorer.repository.data.ResticRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/repositories")
@RequiredArgsConstructor
public class RepositoryController {

    private final RepositoryService repositoryService;
    private final GroupService groupService;

    @GetMapping
    public String list(Model model,
                       @PageableDefault(size = 12, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        model.addAttribute("page", repositoryService.findAll(pageable));
        model.addAttribute("groups", groupService.findAll());
        return "repository/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("repositoryForm", new RepositoryForm());
        model.addAttribute("repositoryTypes", RepositoryType.values());
        model.addAttribute("groups", groupService.findAll());
        return "repository/form";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        ResticRepository repo = repositoryService.findById(id)
                .orElseThrow(() -> new RepositoryNotFoundException(id));
        RepositoryForm form = new RepositoryForm();
        form.setId(repo.getId());
        form.setName(repo.getName());
        form.setType(repo.getType());
        form.setUrl(repo.getUrl());
        // Password fields are left null — Thymeleaf does not render values for type="password".
        // On submit, blank means "keep existing value" (handled in save()).
        form.setS3AccessKey(repo.getProperty(RepositoryPropertyKey.S3_ACCESS_KEY));
        form.setS3Region(repo.getProperty(RepositoryPropertyKey.S3_REGION));
        form.setScanIntervalMinutes(repo.getScanIntervalMinutes());
        form.setCheckIntervalMinutes(repo.getCheckIntervalMinutes());
        form.setEnabled(repo.isEnabled());
        form.setGroupId(repo.getGroup() != null ? repo.getGroup().getId() : null);
        form.setComment(repo.getComment());
        model.addAttribute("repositoryForm", form);
        model.addAttribute("repositoryTypes", RepositoryType.values());
        model.addAttribute("groups", groupService.findAll());
        return "repository/form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute RepositoryForm form,
                       BindingResult result, Model model, RedirectAttributes redirectAttributes) {

        // Manual validation: repositoryPassword is required only for new repositories
        boolean isCreate = form.getId() == null;
        if (isCreate && !org.springframework.util.StringUtils.hasText(form.getRepositoryPassword())) {
            result.rejectValue("repositoryPassword", "validation.password.required",
                    "Repository password is required.");
        }

        if (result.hasErrors()) {
            model.addAttribute("repositoryTypes", RepositoryType.values());
            model.addAttribute("groups", groupService.findAll());
            return "repository/form";
        }
        ResticRepository repo;
        if (!isCreate) {
            repo = repositoryService.findById(form.getId())
                    .orElseThrow(() -> new RepositoryNotFoundException(form.getId()));
        } else {
            repo = new ResticRepository();
        }
        repo.setName(form.getName());
        repo.setType(form.getType());
        repo.setUrl(form.getUrl());

        // Preserve existing sensitive values when the field is left empty or contains the sentinel placeholder
        if (!isCreate) {
            if (org.springframework.util.StringUtils.hasText(form.getRepositoryPassword())
                    && RepositoryForm.isChanged(form.getRepositoryPassword())) {
                repo.setRepositoryPassword(form.getRepositoryPassword());
            }
            if (org.springframework.util.StringUtils.hasText(form.getS3SecretKey())
                    && RepositoryForm.isChanged(form.getS3SecretKey())) {
                repo.setProperty(RepositoryPropertyKey.S3_SECRET_KEY, form.getS3SecretKey());
            }
        } else {
            repo.setRepositoryPassword(form.getRepositoryPassword());
            repo.setProperty(RepositoryPropertyKey.S3_SECRET_KEY, form.getS3SecretKey());
        }
        repo.setProperty(RepositoryPropertyKey.S3_ACCESS_KEY, form.getS3AccessKey());
        repo.setProperty(RepositoryPropertyKey.S3_REGION, form.getS3Region());
        repo.setScanIntervalMinutes(form.getScanIntervalMinutes());
        repo.setCheckIntervalMinutes(form.getCheckIntervalMinutes());
        repo.setEnabled(form.isEnabled());
        repo.setComment(form.getComment());
        if (form.getGroupId() != null) {
            repo.setGroup(groupService.findById(form.getGroupId()).orElse(null));
        } else {
            repo.setGroup(null);
        }
        repositoryService.save(repo);
        redirectAttributes.addFlashAttribute("successMessage", "repository.saved");
        return "redirect:/repositories";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        repositoryService.deleteById(id);
        redirectAttributes.addFlashAttribute("successMessage", "repository.deleted");
        return "redirect:/repositories";
    }
}
