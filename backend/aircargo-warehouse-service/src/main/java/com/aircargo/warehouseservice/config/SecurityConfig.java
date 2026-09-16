package com.aircargo.warehouseservice.config;

import com.aircargo.common.auth.JwtAuthFilter;
import com.aircargo.common.auth.JwtUtil;
import com.aircargo.common.auth.Permissions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@Profile("!test")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtUtil jwtUtil, org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
                                             org.springframework.data.redis.core.StringRedisTemplate redisTemplate) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(eh -> eh.authenticationEntryPoint(new org.springframework.security.web.authentication.HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/receipt-calc-config/**").hasAuthority(Permissions.CAN_READ_RECEIPT)
                .requestMatchers("/api/receipt-calc-config/**").hasAuthority(Permissions.CAN_MANAGE_RECEIPT_CALC)
                .requestMatchers(HttpMethod.GET, "/api/receipts/**", "/api/warehouse/**").hasAuthority(Permissions.CAN_READ_RECEIPT)
                .requestMatchers(HttpMethod.POST, "/api/receipts/**", "/api/warehouse/**").hasAuthority(Permissions.CAN_CREATE_RECEIPT)
                .requestMatchers(HttpMethod.PUT, "/api/receipts/**", "/api/warehouse/**").hasAuthority(Permissions.CAN_UPDATE_RECEIPT)
                .requestMatchers(HttpMethod.PATCH, "/api/receipts/**", "/api/warehouse/**").hasAuthority(Permissions.CAN_UPDATE_RECEIPT)
                .requestMatchers(HttpMethod.DELETE, "/api/receipts/**", "/api/warehouse/**").hasAuthority(Permissions.CAN_DELETE_RECEIPT)
                .anyRequest().authenticated()
            )
            .addFilterBefore(new JwtAuthFilter(jwtUtil, jdbcTemplate, redisTemplate), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        String corsOrigins = System.getenv("CORS_ORIGINS");
        config.setAllowedOrigins(corsOrigins != null && !corsOrigins.isBlank()
                ? List.of(corsOrigins.split("\\s*,\\s*"))
                : List.of("http://localhost:5173", "http://localhost:5174"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
