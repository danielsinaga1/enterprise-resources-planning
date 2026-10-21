package id.enterpriseapps.erp.core.web;

import id.enterpriseapps.erp.core.audit.ErpAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Interceptor ERP untuk {@code /api/erp/**}: gerbang RBAC per rute lalu before-snapshot audit.
 * Rute publik dikecualikan.
 */
@Configuration
@RequiredArgsConstructor
public class ErpWebConfig implements WebMvcConfigurer {

    private final ErpRouteGuard routeGuard;
    private final ErpAuditService audit;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(routeGuard)
                .addPathPatterns("/api/erp/**")
                .excludePathPatterns("/api/erp/public/**", "/api/erp/ga/public/**");
        registry.addInterceptor(new HandlerInterceptor() {
                    @Override
                    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                        audit.captureBefore(request);
                        return true;
                    }
                })
                .addPathPatterns("/api/erp/**")
                .excludePathPatterns("/api/erp/public/**", "/api/erp/ga/public/**");
    }
}
