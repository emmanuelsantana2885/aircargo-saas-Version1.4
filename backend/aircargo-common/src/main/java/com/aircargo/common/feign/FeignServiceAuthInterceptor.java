package com.aircargo.common.feign;

import com.aircargo.common.auth.JwtUtil;
import com.aircargo.common.auth.Permissions;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class FeignServiceAuthInterceptor implements RequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FeignServiceAuthInterceptor.class);
    private static final long TOKEN_REFRESH_MS = 10 * 60 * 1000L;

    private final JwtUtil jwtUtil;
    private final String serviceName;
    private volatile String cachedToken;
    private volatile long cachedAt;

    public FeignServiceAuthInterceptor(
            JwtUtil jwtUtil,
            @Value("${spring.application.name:unknown-service}") String serviceName) {
        this.jwtUtil = jwtUtil;
        this.serviceName = serviceName;
    }

    @Override
    public void apply(RequestTemplate template) {
        if (template.headers().containsKey("Authorization")) {
            return;
        }
        String forwarded = currentUserToken();
        if (forwarded != null) {
            template.header("Authorization", forwarded);
            return;
        }
        template.header("Authorization", "Bearer " + serviceToken());
    }

    private String currentUserToken() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                String header = attrs.getRequest().getHeader("Authorization");
                if (header != null && header.startsWith("Bearer ")) {
                    return header;
                }
            }
        } catch (Exception e) {
            log.debug("No request context for Feign call: {}", e.getMessage());
        }
        return null;
    }

    private String serviceToken() {
        long now = System.currentTimeMillis();
        if (cachedToken != null && now - cachedAt < TOKEN_REFRESH_MS) {
            return cachedToken;
        }
        String token = jwtUtil.generateAccessToken(
                "service:" + serviceName, "SUPER_USER", null, "service@" + serviceName, serviceName,
                Permissions.allCodesAsList());
        cachedToken = token;
        cachedAt = now;
        return token;
    }
}
