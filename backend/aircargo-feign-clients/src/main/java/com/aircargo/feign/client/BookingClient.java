package com.aircargo.feign.client;

import com.aircargo.feign.dto.BookingDTO;
import com.aircargo.feign.fallback.BookingClientFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "booking-service", url = "${booking-service.url:http://localhost:9094}",
        fallbackFactory = BookingClientFallbackFactory.class)
public interface BookingClient {

    @GetMapping("/api/bookings/{id}")
    BookingDTO getBookingById(@PathVariable UUID id);

    @GetMapping("/api/bookings/mawb/{mawbId}")
    BookingDTO getBookingByMawbId(@PathVariable UUID mawbId);

    @GetMapping("/api/bookings/flight/{flightId}")
    List<BookingDTO> getBookingsByFlight(@PathVariable UUID flightId);

    @PostMapping("/api/bookings")
    BookingDTO createBooking(@RequestBody BookingDTO dto);

    @PatchMapping("/api/bookings/{id}/awb")
    void updateBookingAwb(@PathVariable UUID id, @RequestBody Map<String, String> request);
}
