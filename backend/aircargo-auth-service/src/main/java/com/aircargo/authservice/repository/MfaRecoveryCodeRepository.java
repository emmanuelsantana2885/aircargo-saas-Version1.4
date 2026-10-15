package com.aircargo.authservice.repository;

import com.aircargo.authservice.entity.MfaRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, UUID> {

    List<MfaRecoveryCode> findByUserIdAndUsedAtIsNull(UUID userId);

    Optional<MfaRecoveryCode> findByCodeHashAndUsedAtIsNull(String codeHash);
}
