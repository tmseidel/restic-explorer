package org.remus.resticexplorer.admin.web;

import jakarta.validation.Valid;
import org.remus.resticexplorer.admin.AdminService;
import org.remus.resticexplorer.admin.ErrorLogService;
import org.remus.resticexplorer.admin.data.ErrorLogEntry;
import org.springframework.boot.info.BuildProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;
    private final ErrorLogService errorLogService;
    private final Optional<BuildProperties> buildProperties;

    public AdminController(AdminService adminService, ErrorLogService errorLogService,
                           Optional<BuildProperties> buildProperties) {
        this.adminService = adminService;
        this.errorLogService = errorLogService;
        this.buildProperties = buildProperties;
    }

    @GetMapping
    public String adminPanel(Model model) {
        model.addAttribute("changePasswordForm", new ChangePasswordForm());
        model.addAttribute("appVersion", buildProperties.map(BuildProperties::getVersion).orElse("dev"));
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

    private static final int MAX_PAGINATION_PAGES = 20;

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
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();

        Page<ErrorLogEntry> page = errorLogService.findErrors(start, end, pageable);

        // Compute pagination window (max MAX_PAGINATION_PAGES visible page links)
        int totalPages = page.getTotalPages();
        int currentPage = page.getNumber();
        int halfWindow = MAX_PAGINATION_PAGES / 2;
        int paginationStart;
        if (currentPage <= halfWindow) {
            paginationStart = 0;
        } else if (currentPage + halfWindow >= totalPages) {
            paginationStart = Math.max(0, totalPages - MAX_PAGINATION_PAGES);
        } else {
            paginationStart = currentPage - halfWindow;
        }
        int paginationEnd = Math.min(paginationStart + MAX_PAGINATION_PAGES - 1, totalPages - 1);

        model.addAttribute("page", page);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("paginationStart", paginationStart);
        model.addAttribute("paginationEnd", paginationEnd);
        return "admin/error-log";
    }

    @PostMapping("/error-log/delete-all")
    public String deleteAllErrorLogs(RedirectAttributes redirectAttributes) {
        errorLogService.deleteAll();
        redirectAttributes.addFlashAttribute("successMessage", "admin.errorLog.deleteAll.success");
        return "redirect:/admin/error-log";
    }
}
