package com.aircargo.bookingservice.listener;

import com.aircargo.bookingservice.config.RabbitConfig;
import com.aircargo.bookingservice.entity.Booking;
import com.aircargo.bookingservice.repository.BookingRepository;
import com.aircargo.common.event.ReceiptCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Consume receipt.created y si el booking vinculado al MAWB aún no tiene AWB,
 * lo copia del recibo (mawbNumber). Sustituye el sync Féign síncrono
 * syncMawbAndBooking que el warehouse-service hacía hacia bookings.
 * Local: usa BookingRepository directamente (sin Feign, misma BD local).
 */
@Component
public class ReceiptSyncListener {

    private static final Logger log = LoggerFactory.getLogger(ReceiptSyncListener.class);

    private final BookingRepository bookingRepository;

    public ReceiptSyncListener(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @RabbitListener(queues = RabbitConfig.RECEIPT_SYNC_QUEUE)
    @Transactional
    public void onReceiptCreated(ReceiptCreatedEvent event) {
        if (event == null || event.mawbId() == null
                || event.mawbNumber() == null || event.mawbNumber().isBlank()) {
            return;
        }
        try {
            List<Booking> bookings = bookingRepository.findByMawbId(event.mawbId());
            boolean changed = false;
            for (Booking booking : bookings) {
                if (booking.getAwbNumber() == null || booking.getAwbNumber().isBlank()) {
                    booking.setAwbNumber(event.mawbNumber());
                    bookingRepository.save(booking);
                    changed = true;
                    log.info("Booking {} AWB set to {} from receipt {}", booking.getId(), event.mawbNumber(), event.receiptId());
                }
            }
            if (!changed) {
                log.debug("No booking AWB to sync for mawb {} (receipt {})", event.mawbId(), event.receiptId());
            }
        } catch (Exception e) {
            log.error("Failed to sync booking AWB for mawb {}: {}", event.mawbId(), e.getMessage(), e);
        }
    }
}