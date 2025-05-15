package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;

    public SecurityConfig(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        return http.getSharedObject(AuthenticationManagerBuilder.class)
                .userDetailsService(userDetailsService)
                .passwordEncoder(passwordEncoder())
                .and()
                .build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(authz -> authz
                        // Swagger และ API docs
                        .requestMatchers("/swagger","/webjars/**",
                                "/swagger-resources/**",
                                "/swagger-resources",
                                "/configuration/ui",
                                "/configuration/security","/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**","/docs", "/api-docs/**").permitAll()
                        // H2 Console
                        .requestMatchers("/h2-console/**").permitAll()
                        // Static files and public pages
                        .requestMatchers("/", "/index.html", "/static/**", "/css/**", "/js/**", "/img/**", "/favicon.ico").permitAll()
                        // Public APIs
                        .requestMatchers("/api/auth/**").permitAll()
                        // APIs requiring authentication
                        .requestMatchers("/api/courses/**", "/api/lessons/**", "/api/content/**",
                                "/api/exams/**", "/api/progress/**", "/api/user/**").authenticated()
                        // Role-based access
                        .requestMatchers("/api/teacher/**").hasRole("TEACHER")
                        .requestMatchers("/api/student/**").hasRole("STUDENT")
                        .anyRequest().authenticated()
                )
                // Allow frames for H2 console
                .headers(headers -> headers.frameOptions().sameOrigin())
                .formLogin(form -> form.disable())  // Disable form login since we're using REST API
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setContentType("application/json");
                            response.setStatus(200);
                            response.getWriter().write("{\"success\":true}");
                        })
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // به جای setAllowedOrigins از setAllowedOriginPatterns استفاده می‌کنیم
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));  // اجازه همه origins با pattern
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Cache-Control", "Content-Type"));
        configuration.setAllowCredentials(true);  // همچنان true باقی می‌ماند
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}