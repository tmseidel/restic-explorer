package org.remus.resticexplorer.admin.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.remus.resticexplorer.admin.AdminService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/setup")
@RequiredArgsConstructor
public class SetupController {

    private final AdminService adminService;

    @GetMapping
    public String showSetup(Model model) {
        if (adminService.isSetupComplete()) {
            return "redirect:/";
        }
        model.addAttribute("setupForm", new SetupForm());
        return "setup/index";
    }

    @PostMapping
    public String processSetup(@Valid @ModelAttribute SetupForm setupForm,
                                BindingResult result, Model model) {
        if (adminService.isSetupComplete()) {
            return "redirect:/";
        }
        if (!setupForm.getPassword().equals(setupForm.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "validation.password.mismatch", "Passwords do not match");
        }
        if (result.hasErrors()) {
            return "setup/index";
        }
        adminService.createAdmin(setupForm.getPassword());
        return "redirect:/login?setup=true";
    }
}
