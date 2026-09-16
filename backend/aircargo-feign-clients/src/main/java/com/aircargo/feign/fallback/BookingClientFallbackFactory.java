package com.aircargo.feign.fallback;

import com.aircargo.feign.client.BookingClient;
import com.aircargo.feign.dto.BookingDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class BookingClientFallbackFactory implements FallbackFactory<BookingClient> {

    private static final Logger log = LoggerFactory.getLogger(BookingClientFallbackFactory.class);

    @Override
    public BookingClient create(Throwable cause) {
        return new BookingClient() {
            @Override
            public BookingDTO getBookingById(UUID id) {
                log.warn("BookingClient fallback: getBookingById({}) — {}", id, cause.getMessage());
                return null;
            }

            @Override
            public BookingDTO getBookingByMawbId(UUID mawbId) {
                log.warn("BookingClient fallback: getBookingByMawbId({}) — {}", mawbId, cause.getMessage());
                return null;
            }

            @Override
            public List<BookingDTO> getBookingsByFlight(UUID flightId) {
                log.warn("BookingClient fallback: getBookingsByFlight({}) — {}", flightId, cause.getMessage());
                return Collections.emptyList();
            }

            @Override
            public BookingDTO createBooking(BookingDTO dto) {
                log.error("BookingClient fallback: createBooking — writes cannot degrade", cause);
                throw new RuntimeException("Booking service unavailable: " + cause.getMessage(), cause);
            }

            @Override
            public void updateBookingAwb(UUID id, Map<String, String> request) {
                log.error("BookingClient fallback: updateBookingAwb({}) — writes cannot degrade", id, cause);
                throw new RuntimeException("Booking service unavailable: " + cause.getMessage(), cause);
            }
        };
    }
}
