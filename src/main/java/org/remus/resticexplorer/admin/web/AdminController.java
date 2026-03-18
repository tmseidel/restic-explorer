package org.remus.resticexplorer.admin.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.remus.resticexplorer.admin.AdminService;
import org.remus.resticexplorer.admin.ErrorLogService;
import org.remus.resticexplorer.admin.data.ErrorLogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ErrorLogService errorLogService;

    @GetMapping
    public String adminPanel(Model model) {
        model.addAttribute("changePasswordForm", new ChangePasswordForm());
        return "admin/index";
    }

    @PostMapping("/change-password")
    public String changePassword(@Valid @ModelAttribute ChangePasswordForm form,
                                  BindingResult result, RedirectAttributes redirectAttributes) {
        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "validation.password.mismatch", "Passwords do not match");
        }
        if (result.hasErrors()) {
            return "admin/index";
        }
        adminService.changePassword(form.getNewPassword());
        redirectAttributes.addFlashAttribute("successMessage", "message.passwordChanged");
        return "redirect:/admin";
    }

    @GetMapping("/error-log")
    public String errorLog(Model model,
                           @RequestParam(required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                           @RequestParam(required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                           @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC)
                           Pageable pageable) {
        if (startDate == null) {
            startDate = LocalDate.now().minusDays(7);
        }
        if (endDate == null) {
            endDate = LocalDate.now();
        }
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);

        Page<ErrorLogEntry> page = errorLogService.findErrors(start, end, pageable);

        model.addAttribute("page", page);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        return "admin/error-log";
    }
}
