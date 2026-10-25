package id.neravoxa.config;

import id.neravoxa.exception.CustomAuthenticationException;
import id.neravoxa.property.AppConfigProperties;
import id.neravoxa.property.HstsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final AppConfigProperties appConfigProperties;
        private final HstsProperties hstsProperties;
        // private final SpringSecurityProperties springSecurityProperties;
        private final AuthenticationConfig authenticationConfig;
        private final CustomAuthenticationException customAuthenticationException;

        private final String[] AUTH_WHITELIST = {
                        // Observability: Prometheus scrapes /actuator/prometheus and the
                        // Docker/k8s probes hit /actuator/health. These carry no sensitive
                        // payloads and are reached over the internal network only.
                        // NOTE: in production keep these off the public ingress (prod binds
                        // server.address=127.0.0.1) or guard them at the reverse proxy.
                        "/actuator/health/**",
                        "/actuator/info",
                        "/actuator/prometheus",
                        "/actuator/metrics/**",
                        "/api/docs/**",
                        "/api/docs/swagger-ui/**",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh-token",
                        "/api/v1/profile/**",
                        "/api/rbac/**",
                        // ERP: formulir inquiry publik, branding & scan QR aset (tanpa login).
                        "/api/erp/public/**",
                        "/api/erp/ga/public/**",
                        "/uploads/**"
        };

        private final String[] BASIC_AUTH = {
                        "/actuator/health",
        };

        private final String[] BEARER_TOKEN = {
                        "/api/**"
        };

        // @Bean
        // public UserDetailsService userDetailsService() {
        // return username -> {
        // if (username.equals(springSecurityProperties.getUser().getName())) {
        // return User
        // .withUsername(springSecurityProperties.getUser().getName())
        // .password(passwordEncoder().encode(new
        // String(springSecurityProperties.getUser().getPassword())))
        // .roles(springSecurityProperties.getUser().getRole()).build();
        // }
        // throw new BadCredentialsException("User not found");
        // };
        // }

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                return http
                                .csrf(AbstractHttpConfigurer::disable)
                                .cors(withDefaults())
                                .formLogin(AbstractHttpConfigurer::disable)
                                .authorizeHttpRequests(req -> req
                                                .requestMatchers(AUTH_WHITELIST).permitAll()
                                                // Branding dibaca halaman Login sebelum ada token; hanya
                                                // GET yang dibuka, penulisan tetap jatuh ke BEARER_TOKEN.
                                                .requestMatchers(HttpMethod.GET, "/api/v1/settings/general").permitAll()
                                                .requestMatchers(BASIC_AUTH).authenticated()
                                                .requestMatchers(BEARER_TOKEN).authenticated()
                                                .anyRequest().permitAll())
                                .headers(header -> header
                                                // HSTS menempel di browser pengguna dan tidak bisa
                                                // dibatalkan dari server, jadi parameternya dibuat
                                                // bisa diatur — lihat HstsProperties untuk alasannya
                                                // dan untuk jebakan sertifikat self-signed.
                                                .httpStrictTransportSecurity(hsts -> {
                                                        if (!hstsProperties.isEnabled()) {
                                                                hsts.disable();
                                                                return;
                                                        }
                                                        hsts.maxAgeInSeconds(hstsProperties.getMaxAgeSeconds())
                                                                .includeSubDomains(hstsProperties.isIncludeSubDomains())
                                                                .preload(hstsProperties.isPreload());
                                                        if (hstsProperties.isAllRequests()) {
                                                                hsts.requestMatcher(AnyRequestMatcher.INSTANCE);
                                                        }
                                                })
                                                .referrerPolicy(contentRefererPolicy -> contentRefererPolicy
                                                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                                                .contentSecurityPolicy(
                                                                contentSecurityPolicyConfig -> contentSecurityPolicyConfig
                                                                                .policyDirectives(
                                                                                                "default-src 'self'; script-src 'self'; object-src 'self'; frame-ancestors 'self'")))
                                .sessionManagement(
                                                sessionManagement -> sessionManagement
                                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .httpBasic(withDefaults())
                                .exceptionHandling(
                                                exceptionHandling -> exceptionHandling
                                                                .authenticationEntryPoint(customAuthenticationException)
                                                                .accessDeniedHandler((request, response, accessDeniedException) -> {
                                                                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                                                        response.setContentType("application/json");
                                                                        response.getWriter().write(
                                                                                        "{\"status\":\"FAILED\",\"httpStatus\":\"FORBIDDEN\",\"errorCode\":\"403\",\"errorMessage\":\"Anda tidak memiliki akses untuk operasi ini\"}");
                                                                }))
                                .addFilterBefore(authenticationConfig, UsernamePasswordAuthenticationFilter.class)
                                .build();
        }

        // @Bean
        // AuthenticationManager authenticationManager(HttpSecurity http) throws
        // Exception {
        // AuthenticationManagerBuilder authenticationManagerBuilder =
        // http.getSharedObject(AuthenticationManagerBuilder.class);
        // authenticationManagerBuilder.userDetailsService(userDetailsService()).passwordEncoder(passwordEncoder());
        // return authenticationManagerBuilder.build();
        // }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOriginPatterns(
                        Arrays.stream(appConfigProperties.getAllowedOrigins().split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .toList()
                );
                configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
                configuration.setAllowedHeaders(List.of("*"));
                configuration.setAllowCredentials(true);
                configuration.addExposedHeader("Signature");
                configuration.addExposedHeader("Authorization");

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }

        @Bean
        public CorsFilter corsFilter() {
                return new CorsFilter(corsConfigurationSource());
        }

}
