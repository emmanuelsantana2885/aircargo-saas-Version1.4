package com.aircargo.flightservice.config;

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
                .requestMatchers(HttpMethod.GET, "/api/airlines/**").hasAuthority(Permissions.CAN_READ_AIRLINE)
                .requestMatchers("/api/airlines/**").hasAuthority(Permissions.CAN_MANAGE_AIRLINE)
                .requestMatchers(HttpMethod.GET, "/api/aircraft-types/**").hasAuthority(Permissions.CAN_READ_AIRCRAFT_TYPE)
                .requestMatchers(HttpMethod.GET, "/api/flights/**").hasAuthority(Permissions.CAN_READ_FLIGHT)
                .requestMatchers("/api/flights/**")
                    .hasAnyAuthority(Permissions.CAN_CREATE_FLIGHT, Permissions.CAN_UPDATE_FLIGHT, Permissions.CAN_DELETE_FLIGHT)
                .requestMatchers("/api/**").authenticated()
                .anyRequest().authenticated()
            )
            .exceptionHandling(eh -> eh.authenticationEntryPoint(
                new org.springframework.security.web.authentication.HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED)))
            .addFilterBefore(new JwtAuthFilter(jwtUtil, jdbcTemplate, redisTemplate), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
