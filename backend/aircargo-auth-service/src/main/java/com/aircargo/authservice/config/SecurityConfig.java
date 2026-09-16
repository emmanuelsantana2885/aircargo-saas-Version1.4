package com.aircargo.authservice.config;

import com.aircargo.common.auth.JwtAuthFilter;
import com.aircargo.common.auth.JwtUtil;
import com.aircargo.common.auth.Permissions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("!test")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtUtil jwtUtil,
                                           org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
                                           org.springframework.data.redis.core.StringRedisTemplate redisTemplate) throws Exception {
        http
            .cors(withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/block/**").hasAuthority(Permissions.CAN_MANAGE_USER)
                .requestMatchers(HttpMethod.POST, "/api/auth/unblock/**").hasAuthority(Permissions.CAN_MANAGE_USER)
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/users/connected").hasAnyAuthority(Permissions.CAN_READ_DASHBOARD, Permissions.CAN_READ_AUDIT)
                .requestMatchers("/api/users/**").hasAuthority(Permissions.CAN_MANAGE_USER)
                .requestMatchers("/api/role-permissions/**").hasAuthority(Permissions.CAN_MANAGE_ROLES)
                .requestMatchers("/api/audit-logs/**").hasAuthority(Permissions.CAN_READ_AUDIT)
                .requestMatchers(HttpMethod.GET, "/api/sites/**").hasAuthority(Permissions.CAN_READ_SITE)
                .requestMatchers("/api/sites/**").hasAuthority(Permissions.CAN_MANAGE_SITE)
                .requestMatchers("/api/commodity-types/**").hasAuthority(Permissions.CAN_MANAGE_COMMODITY_TYPE)
                .requestMatchers("/api/backup/**").hasAuthority(Permissions.CAN_MANAGE_BACKUP)
                .anyRequest().authenticated()
            )
            .exceptionHandling(eh -> eh.authenticationEntryPoint(
                new org.springframework.security.web.authentication.HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED)))
            .addFilterBefore(new JwtAuthFilter(jwtUtil, jdbcTemplate, redisTemplate), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
