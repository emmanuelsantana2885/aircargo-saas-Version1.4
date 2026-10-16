package com.aircargo.warehouseservice.service;

import com.aircargo.common.event.ReceiptCreatedEvent;
import com.aircargo.feign.client.MawbClient;
import com.aircargo.warehouseservice.calc.CalcParams;
import com.aircargo.warehouseservice.calc.CalcParamsResolver;
import com.aircargo.warehouseservice.config.RabbitConfig;
import com.aircargo.warehouseservice.dto.ReceiptPieceDTO;
import com.aircargo.warehouseservice.dto.WarehouseReceiptDTO;
import com.aircargo.warehouseservice.entity.WarehouseReceipt;
import com.aircargo.warehouseservice.repository.ReceiptPieceRepository;
import com.aircargo.warehouseservice.repository.WarehouseReceiptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guard de regresión del sync event-driven recibo → booking/MAWB:
 * tanto emitReceipt (alta) como updateReceipt (edición) deben publicar
 * receipt.created en el exchange compartido, para que los listeners de
 * booking (rellenar AWB) y mawb (BOOKED→RECEIVED) se disparen también
 * cuando el recibo se completa editando un borrador en vez de emitirlo.
 */
@ExtendWith(MockitoExtension.class)
class WarehouseServiceImplTest {

    @Mock private WarehouseReceiptRepository receiptRepository;
    @Mock private ReceiptPieceRepository pieceRepository;
    @Mock private MawbClient mawbClient;
    @Mock private ReceiptExportService receiptExportService;
    @Mock private ReceiptFullPdfService receiptFullPdfService;
    @Mock private PdfGenerationService pdfGenerationService;
    @Mock private ObjectMapper objectMapper;
    @Mock private MawbNumberResolver mawbNumberResolver;
    @Mock private CalcParamsResolver calcParamsResolver;
    @Mock private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private WarehouseServiceImpl service;

    private static final String MAWB_NUMBER = "406-05912970";

    private ReceiptPieceDTO samplePiece() {
        ReceiptPieceDTO p = new ReceiptPieceDTO();
        p.setPieces(1);
        p.setLengthIn(BigDecimal.TEN);
        p.setWidthIn(BigDecimal.TEN);
        p.setHeightIn(BigDecimal.TEN);
        p.setScaleWeightLbs(new BigDecimal("100"));
        return p;
    }

    private WarehouseReceiptDTO sampleDto(UUID mawbId) {
        WarehouseReceiptDTO dto = new WarehouseReceiptDTO();
        dto.setMawbId(mawbId);
        dto.setShipperName("SHIPPER CO");
        dto.setConsigneeName("CONSIGNEE CO");
        dto.setOrigin("SDQ");
        dto.setDestination("MIA");
        dto.setPrintName("Tester");
        dto.setPieces(List.of(samplePiece()));
        return dto;
    }

    @Test
    void emitReceipt_publishesReceiptCreatedEvent() {
        UUID mawbId = UUID.randomUUID();
        when(mawbNumberResolver.resolve(any(), any())).thenReturn(MAWB_NUMBER);
        when(calcParamsResolver.resolveFor(any(), any())).thenReturn(CalcParams.defaults());
        when(receiptRepository.save(any(WarehouseReceipt.class))).thenAnswer(inv -> {
            WarehouseReceipt e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });

        service.emitReceipt(sampleDto(mawbId), null, null);

        ArgumentCaptor<ReceiptCreatedEvent> captor = ArgumentCaptor.forClass(ReceiptCreatedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RECEIPT_CREATED_KEY), captor.capture());
        assertEquals(mawbId, captor.getValue().mawbId());
        assertEquals(MAWB_NUMBER, captor.getValue().mawbNumber());
    }

    @Test
    void updateReceipt_publishesReceiptCreatedEvent() {
        UUID receiptId = UUID.randomUUID();
        UUID mawbId = UUID.randomUUID();
        WarehouseReceipt existing = new WarehouseReceipt();
        existing.setId(receiptId);
        existing.setMawbId(mawbId);
        existing.setSuperseded(false);

        when(receiptRepository.findById(any())).thenReturn(Optional.of(existing));
        when(receiptRepository.save(any(WarehouseReceipt.class))).thenReturn(existing);
        when(mawbNumberResolver.resolve(any(), any())).thenReturn(MAWB_NUMBER);
        when(calcParamsResolver.resolveFor(any(), any())).thenReturn(CalcParams.defaults());

        Optional<WarehouseReceiptDTO> result = service.updateReceipt(receiptId, sampleDto(mawbId), null, null);

        assertEquals(true, result.isPresent());
        ArgumentCaptor<ReceiptCreatedEvent> captor = ArgumentCaptor.forClass(ReceiptCreatedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitConfig.EXCHANGE), eq(RabbitConfig.RECEIPT_CREATED_KEY), captor.capture());
        assertEquals(mawbId, captor.getValue().mawbId());
        assertEquals(MAWB_NUMBER, captor.getValue().mawbNumber());
    }
}
