package com.aircargo.mawbservice.config;

import com.aircargo.common.auth.JwtAuthFilter;
import com.aircargo.common.auth.JwtUtil;
import com.aircargo.common.auth.Permissions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;
import static org.springframework.security.config.Customizer.withDefaults;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

@Configuration
@EnableWebSecurity
@Profile("!test")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtUtil jwtUtil, org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
                                            org.springframework.data.redis.core.StringRedisTemplate redisTemplate) throws Exception {
        http
            .cors(withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/compliance/**").hasAuthority(Permissions.CAN_MANAGE_COMPLIANCE)
                .requestMatchers(HttpMethod.PUT, "/api/compliance/**").hasAuthority(Permissions.CAN_MANAGE_COMPLIANCE)
                .requestMatchers(HttpMethod.DELETE, "/api/compliance/**").hasAuthority(Permissions.CAN_MANAGE_COMPLIANCE)
                .requestMatchers(HttpMethod.POST, "/api/label-templates/**").hasAuthority(Permissions.CAN_MANAGE_LABEL_TEMPLATE)
                .requestMatchers(HttpMethod.PUT, "/api/label-templates/**").hasAuthority(Permissions.CAN_MANAGE_LABEL_TEMPLATE)
                .requestMatchers(HttpMethod.DELETE, "/api/label-templates/**").hasAuthority(Permissions.CAN_MANAGE_LABEL_TEMPLATE)
                .requestMatchers(HttpMethod.GET, "/api/mawbs/**").hasAuthority(Permissions.CAN_READ_MAWB)
                .requestMatchers(HttpMethod.POST, "/api/mawbs/labels").hasAuthority(Permissions.CAN_PRINT_LABEL)
                .requestMatchers(HttpMethod.PUT, "/api/mawbs/*/supporting-docs").hasAuthority(Permissions.CAN_MANAGE_MAWB_DOCS)
                .requestMatchers(HttpMethod.PUT, "/api/mawbs/*/supporting-docs/**").hasAuthority(Permissions.CAN_MANAGE_MAWB_DOCS)
                .requestMatchers(HttpMethod.POST, "/api/mawbs/**").hasAuthority(Permissions.CAN_CREATE_MAWB)
                .requestMatchers(HttpMethod.PUT, "/api/mawbs/**").hasAuthority(Permissions.CAN_UPDATE_MAWB)
                .requestMatchers(HttpMethod.PATCH, "/api/mawbs/**").hasAuthority(Permissions.CAN_UPDATE_MAWB)
                .requestMatchers(HttpMethod.DELETE, "/api/mawbs/**").hasAuthority(Permissions.CAN_DELETE_MAWB)
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )
            .exceptionHandling(eh -> eh.authenticationEntryPoint(
                new org.springframework.security.web.authentication.HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED)))
            .addFilterBefore(new JwtAuthFilter(jwtUtil, jdbcTemplate, redisTemplate), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
