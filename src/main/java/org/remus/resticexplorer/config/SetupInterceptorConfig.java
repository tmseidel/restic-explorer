package org.remus.resticexplorer.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.remus.resticexplorer.admin.AdminService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
public class SetupInterceptorConfig implements WebMvcConfigurer {

    private final AdminService adminService;

    public SetupInterceptorConfig(AdminService adminService) {
        this.adminService = adminService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SetupInterceptor())
                .excludePathPatterns("/setup/**", "/css/**", "/js/**", "/images/**", "/actuator/**");
    }

    private class SetupInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            if (!adminService.isSetupComplete()) {
                response.sendRedirect("/setup");
                return false;
            }
            return true;
        }
    }
}
