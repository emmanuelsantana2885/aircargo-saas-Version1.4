package com.aircargo.feign.fallback;

import com.aircargo.feign.client.AuthClient;
import com.aircargo.feign.dto.UserDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
public class AuthClientFallbackFactory implements FallbackFactory<AuthClient> {

    private static final Logger log = LoggerFactory.getLogger(AuthClientFallbackFactory.class);

    @Override
    public AuthClient create(Throwable cause) {
        return new AuthClient() {
            @Override
            public UserDTO getUserById(UUID id) {
                log.warn("AuthClient fallback: getUserById({}) — {}", id, cause.getMessage());
                return null;
            }

            @Override
            public List<UserDTO> getAllUsers() {
                log.warn("AuthClient fallback: getAllUsers — {}", cause.getMessage());
                return Collections.emptyList();
            }
        };
    }
}
