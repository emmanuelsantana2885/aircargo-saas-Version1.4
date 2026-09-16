package com.aircargo.warehouseservice.service;

import com.aircargo.warehouseservice.calc.CalcParams;
import com.aircargo.warehouseservice.calc.CalcParamsResolver;
import com.aircargo.warehouseservice.calc.ChargeableMethod;
import com.aircargo.warehouseservice.dto.ReceiptCalcConfigDTO;
import com.aircargo.warehouseservice.entity.ReceiptCalcConfig;
import com.aircargo.warehouseservice.repository.ReceiptCalcConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ReceiptCalcConfigServiceImpl implements ReceiptCalcConfigService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptCalcConfigServiceImpl.class);

    private final ReceiptCalcConfigRepository repository;
    private final CalcParamsResolver resolver;

    public ReceiptCalcConfigServiceImpl(ReceiptCalcConfigRepository repository, CalcParamsResolver resolver) {
        this.repository = repository;
        this.resolver = resolver;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReceiptCalcConfigDTO> getAll() {
        return repository.findAllByOrderByAirlineIdAscIdAsc().stream()
                .map(ReceiptCalcConfigDTO::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CalcParams resolve(UUID airlineId) {
        return resolver.resolveProfile(airlineId);
    }

    @Override
    @Transactional(readOnly = true)
    public ReceiptCalcConfigDTO getDefault() {
        return ReceiptCalcConfigDTO.fromEntity(defaultEntity());
    }

    @Override
    @Transactional
    public ReceiptCalcConfigDTO saveDefault(ReceiptCalcConfigDTO dto) {
        ReceiptCalcConfig entity = repository.findTopByAirlineIdIsNull()
                .orElseGet(ReceiptCalcConfig::new);
        dto.applyTo(entity);
        ReceiptCalcConfig saved = repository.save(entity);
        log.info("Receipt calc default config saved (dimDom={}, dimIntl={}, method={})",
                saved.getDimFactorDom(), saved.getDimFactorIntl(), saved.getChargeableMethod());
        return ReceiptCalcConfigDTO.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ReceiptCalcConfigDTO getByAirline(UUID airlineId) {
        if (airlineId == null) throw new IllegalArgumentException("El airlineId es obligatorio");
        return ReceiptCalcConfigDTO.fromEntity(entityForAirline(airlineId));
    }

    @Override
    @Transactional
    public ReceiptCalcConfigDTO saveForAirline(UUID airlineId, ReceiptCalcConfigDTO dto) {
        if (airlineId == null) throw new IllegalArgumentException("El airlineId es obligatorio");
        ReceiptCalcConfig entity = repository.findTopByAirlineId(airlineId).orElseGet(ReceiptCalcConfig::new);
        entity.setAirlineId(airlineId);
        dto.applyTo(entity);
        ReceiptCalcConfig saved = repository.save(entity);
        log.info("Receipt calc config saved for airline {} (dimDom={}, dimIntl={}, method={})",
                airlineId, saved.getDimFactorDom(), saved.getDimFactorIntl(), saved.getChargeableMethod());
        return ReceiptCalcConfigDTO.fromEntity(saved);
    }

    @Override
    @Transactional
    public void deleteForAirline(UUID airlineId) {
        if (airlineId == null) throw new IllegalArgumentException("El airlineId es obligatorio");
        repository.deleteByAirlineId(airlineId);
        log.info("Receipt calc config deleted for airline {}", airlineId);
    }

    private ReceiptCalcConfig defaultEntity() {
        return repository.findTopByAirlineIdIsNull().orElseGet(() -> {
            ReceiptCalcConfig entity = new ReceiptCalcConfig();
            entity.setChargeableMethod(ChargeableMethod.MAX);
            entity.setDimFactorDom(CalcParams.DEFAULT_DIM_FACTOR_DOM);
            entity.setDimFactorIntl(CalcParams.DEFAULT_DIM_FACTOR_INTL);
            entity.setRoundUpKg(java.math.BigDecimal.ZERO);
            entity.setRoundUpLbs(java.math.BigDecimal.ZERO);
            entity.setMinChargeableKg(java.math.BigDecimal.ZERO);
            entity.setMinChargeableLbs(java.math.BigDecimal.ZERO);
            return entity;
        });
    }

    private ReceiptCalcConfig entityForAirline(UUID airlineId) {
        return repository.findTopByAirlineId(airlineId)
                .orElseThrow(() -> new IllegalArgumentException("No existe configuración de cálculo para la aerolínea " + airlineId));
    }
}