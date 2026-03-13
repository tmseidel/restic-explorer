package org.remus.resticexplorer.repository.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.remus.resticexplorer.repository.RepositoryService;
import org.remus.resticexplorer.repository.data.RepositoryType;
import org.remus.resticexplorer.repository.data.ResticRepository;
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

    @GetMapping
    public String list(Model model) {
        model.addAttribute("repositories", repositoryService.findAll());
        return "repository/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("repositoryForm", new RepositoryForm());
        model.addAttribute("repositoryTypes", RepositoryType.values());
        return "repository/form";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        ResticRepository repo = repositoryService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + id));
        RepositoryForm form = new RepositoryForm();
        form.setId(repo.getId());
        form.setName(repo.getName());
        form.setType(repo.getType());
        form.setUrl(repo.getUrl());
        form.setRepositoryPassword(repo.getRepositoryPassword());
        form.setS3AccessKey(repo.getS3AccessKey());
        form.setS3SecretKey(repo.getS3SecretKey());
        form.setS3Region(repo.getS3Region());
        form.setScanIntervalMinutes(repo.getScanIntervalMinutes());
        form.setEnabled(repo.isEnabled());
        model.addAttribute("repositoryForm", form);
        model.addAttribute("repositoryTypes", RepositoryType.values());
        return "repository/form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute RepositoryForm form,
                       BindingResult result, Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("repositoryTypes", RepositoryType.values());
            return "repository/form";
        }
        ResticRepository repo;
        if (form.getId() != null) {
            repo = repositoryService.findById(form.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + form.getId()));
        } else {
            repo = new ResticRepository();
        }
        repo.setName(form.getName());
        repo.setType(form.getType());
        repo.setUrl(form.getUrl());
        repo.setRepositoryPassword(form.getRepositoryPassword());
        repo.setS3AccessKey(form.getS3AccessKey());
        repo.setS3SecretKey(form.getS3SecretKey());
        repo.setS3Region(form.getS3Region());
        repo.setScanIntervalMinutes(form.getScanIntervalMinutes());
        repo.setEnabled(form.isEnabled());
        repositoryService.save(repo);
        redirectAttributes.addFlashAttribute("successMessage", "Repository saved successfully");
        return "redirect:/repositories";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        repositoryService.deleteById(id);
        redirectAttributes.addFlashAttribute("successMessage", "Repository deleted successfully");
        return "redirect:/repositories";
    }
}
