// Desarrollado por Emmanuel Santana Solano
package com.aircargo.auditreportsservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.aircargo.auditreportsservice", "com.aircargo.common"})
@EnableCaching
@EnableFeignClients(basePackages = "com.aircargo.feign.client")
public class AuditReportsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditReportsServiceApplication.class, args);
    }
}
