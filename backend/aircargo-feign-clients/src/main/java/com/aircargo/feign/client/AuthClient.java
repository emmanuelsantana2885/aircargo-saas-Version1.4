package com.aircargo.feign.client;

import com.aircargo.feign.dto.UserDTO;
import com.aircargo.feign.fallback.AuthClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "auth-service", url = "${auth-service.url:http://localhost:9092}",
        fallbackFactory = AuthClientFallbackFactory.class)
public interface AuthClient {

    @GetMapping("/api/users/{id}")
    UserDTO getUserById(@PathVariable UUID id);

    @GetMapping("/api/users")
    List<UserDTO> getAllUsers();
}
