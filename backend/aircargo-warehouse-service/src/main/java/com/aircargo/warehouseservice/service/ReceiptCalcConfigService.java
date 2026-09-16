package com.aircargo.warehouseservice.service;

import com.aircargo.warehouseservice.calc.CalcParams;
import com.aircargo.warehouseservice.dto.ReceiptCalcConfigDTO;

import java.util.List;
import java.util.UUID;

public interface ReceiptCalcConfigService {

    List<ReceiptCalcConfigDTO> getAll();

    CalcParams resolve(UUID airlineId);

    ReceiptCalcConfigDTO getDefault();

    ReceiptCalcConfigDTO saveDefault(ReceiptCalcConfigDTO dto);

    ReceiptCalcConfigDTO getByAirline(UUID airlineId);

    ReceiptCalcConfigDTO saveForAirline(UUID airlineId, ReceiptCalcConfigDTO dto);

    void deleteForAirline(UUID airlineId);
}