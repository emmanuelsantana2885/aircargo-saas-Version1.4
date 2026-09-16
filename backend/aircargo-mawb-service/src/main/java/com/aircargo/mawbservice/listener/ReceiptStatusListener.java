package com.aircargo.mawbservice.listener;

import com.aircargo.common.event.ReceiptCreatedEvent;
import com.aircargo.mawbservice.config.RabbitConfig;
import com.aircargo.mawbservice.entity.MawbStatus;
import com.aircargo.mawbservice.service.MawbService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consume receipt.created y pone el MAWB en RECEIVED si el recibo está completo
 * y el MAWB estaba en BOOKED.
 * Sustituye el sync Féign síncrono updateMawbStatusToReceived que el
 * warehouse-service hacía hacia mawb-service. setStatus/BOOKED→RECEIVED
 * se hace vía MawbService.updateStatus para que se propague por AMQP
 * (mawb.updated / mawb.status.changed) a uld y load-planning.
 */
@Component
public class ReceiptStatusListener {

    private static final Logger log = LoggerFactory.getLogger(ReceiptStatusListener.class);

    private final MawbService mawbService;

    public ReceiptStatusListener(MawbService mawbService) {
        this.mawbService = mawbService;
    }

    @RabbitListener(queues = RabbitConfig.RECEIPT_SYNC_QUEUE)
    @Transactional
    public void onReceiptCreated(ReceiptCreatedEvent event) {
        if (event == null || event.mawbId() == null) {
            return;
        }
        // Solo procesar si el recibo está completado (todos los pasos)
        if (!event.completed()) {
            log.debug("Receipt {} not completed yet, skipping MAWB status update", event.receiptId());
            return;
        }
        try {
            mawbService.getById(event.mawbId()).ifPresent(mawb -> {
                if (mawb.getStatus() == MawbStatus.BOOKED) {
                    mawbService.updateStatus(event.mawbId(), MawbStatus.RECEIVED);
                    log.info("Auto-set MAWB {} status to RECEIVED from completed receipt {}", event.mawbId(), event.receiptId());
                } else {
                    log.debug("MAWB {} status is {}, not BOOKED — no status change from receipt {}",
                            event.mawbId(), mawb.getStatus(), event.receiptId());
                }
            });
        } catch (Exception e) {
            log.error("Failed to auto-set MAWB {} status to RECEIVED: {}", event.mawbId(), e.getMessage(), e);
        }
    }
}