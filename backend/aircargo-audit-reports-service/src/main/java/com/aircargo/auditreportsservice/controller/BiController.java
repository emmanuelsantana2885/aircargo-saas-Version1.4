package com.aircargo.auditreportsservice.controller;

import com.aircargo.auditreportsservice.service.BiService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bi")
public class BiController {

    private final BiService biService;

    public BiController(BiService biService) {
        this.biService = biService;
    }

    @GetMapping("/flights")
    public ResponseEntity<List<Map<String, Object>>> getFlights(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ResponseEntity.ok(biService.getFlights(dateFrom, dateTo));
    }

    @GetMapping("/bookings")
    public ResponseEntity<List<Map<String, Object>>> getBookings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ResponseEntity.ok(biService.getBookings(dateFrom, dateTo));
    }

    @GetMapping("/mawbs")
    public ResponseEntity<List<Map<String, Object>>> getMawbs() {
        return ResponseEntity.ok(biService.getMawbs());
    }

    @GetMapping("/receipts")
    public ResponseEntity<List<Map<String, Object>>> getReceipts() {
        return ResponseEntity.ok(biService.getReceipts());
    }

    @GetMapping("/ulds")
    public ResponseEntity<List<Map<String, Object>>> getUlds() {
        return ResponseEntity.ok(biService.getUlds());
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        return ResponseEntity.ok(biService.getDashboard());
    }

    @GetMapping("/daily")
    public ResponseEntity<List<Map<String, Object>>> getDaily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ResponseEntity.ok(biService.getDaily(dateFrom, dateTo));
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ResponseEntity.ok(biService.getSummary(dateFrom, dateTo));
    }

    @GetMapping("/by-location")
    public ResponseEntity<List<Map<String, Object>>> getByLocation() {
        return ResponseEntity.ok(biService.getByLocation());
    }

    @GetMapping("/timeline")
    public ResponseEntity<List<Map<String, Object>>> getTimeline(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ResponseEntity.ok(biService.getTimeline(dateFrom, dateTo));
    }

    @GetMapping("/top-mawbs")
    public ResponseEntity<List<Map<String, Object>>> getTopMawbs(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(biService.getTopMawbs(limit));
    }

    @GetMapping("/flight-performance")
    public ResponseEntity<List<Map<String, Object>>> getFlightPerformance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ResponseEntity.ok(biService.getFlightPerformance(dateFrom, dateTo));
    }
}
