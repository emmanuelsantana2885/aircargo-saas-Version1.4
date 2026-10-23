package com.aircargo.auditreportsservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getCatalog() {
        List<Map<String, Object>> catalog = new ArrayList<>();

        catalog.add(serviceEntry("bi", "Business Intelligence", "/api/bi", List.of(
            endpoint("GET", "/dashboard", "Dashboard KPIs", List.of(), "ADMIN, SUPER_USER, BI_USER"),
            endpoint("GET", "/summary", "Aggregated summary by date range", List.of("dateFrom", "dateTo"), "ADMIN, SUPER_USER, BI_USER"),
            endpoint("GET", "/flights", "Flights BI data", List.of("dateFrom", "dateTo"), "ADMIN, SUPER_USER, BI_USER"),
            endpoint("GET", "/bookings", "Bookings BI data", List.of("dateFrom", "dateTo"), "ADMIN, SUPER_USER, BI_USER"),
            endpoint("GET", "/mawbs", "MAWBs BI data", List.of(), "ADMIN, SUPER_USER, BI_USER"),
            endpoint("GET", "/receipts", "Receipts BI data", List.of(), "ADMIN, SUPER_USER, BI_USER"),
            endpoint("GET", "/ulds", "ULDs BI data", List.of(), "ADMIN, SUPER_USER, BI_USER"),
            endpoint("GET", "/daily", "Daily aggregated data", List.of("dateFrom", "dateTo"), "ADMIN, SUPER_USER, BI_USER"),
            endpoint("GET", "/by-location", "Metrics grouped by origin location", List.of(), "ADMIN, SUPER_USER, BI_USER"),
            endpoint("GET", "/timeline", "Daily timeline aggregates", List.of("dateFrom", "dateTo"), "ADMIN, SUPER_USER, BI_USER"),
            endpoint("GET", "/top-mawbs", "Top MAWBs by weight", List.of("limit"), "ADMIN, SUPER_USER, BI_USER"),
            endpoint("GET", "/flight-performance", "Flight utilization metrics", List.of("dateFrom", "dateTo"), "ADMIN, SUPER_USER, BI_USER")
        )));

        catalog.add(serviceEntry("exports", "Data Exports", "/api/exports", List.of(
            endpoint("GET", "/{type}", "Export entity data (flights/bookings/mawbs/receipts/ulds)", List.of("format", "dateFrom", "dateTo", "audit"), "READ_ONLY+")
        )));

        catalog.add(serviceEntry("reports", "Downloadable Reports", "/api/reports", List.of(
            endpoint("GET", "/daily", "Daily operations report", List.of("date", "format"), "ADMIN, SUPER_USER, BI_USER"),
            endpoint("GET", "/weekly", "Weekly operations report", List.of("weekStart", "format"), "ADMIN, SUPER_USER, BI_USER"),
            endpoint("GET", "/flights", "Flights report export", List.of("dateFrom", "dateTo", "format"), "ADMIN, SUPER_USER, BI_USER"),
            endpoint("GET", "/by-location", "Location-based report", List.of("format"), "ADMIN, SUPER_USER, BI_USER")
        )));

        return ResponseEntity.ok(catalog);
    }

    private Map<String, Object> serviceEntry(String service, String description, String basePath, List<Map<String, Object>> endpoints) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("service", service);
        entry.put("description", description);
        entry.put("basePath", basePath);
        entry.put("endpoints", endpoints);
        return entry;
    }

    private Map<String, Object> endpoint(String method, String path, String description, List<String> params, String auth) {
        Map<String, Object> ep = new LinkedHashMap<>();
        ep.put("method", method);
        ep.put("path", path);
        ep.put("description", description);
        ep.put("parameters", params);
        ep.put("auth", auth);
        return ep;
    }
}
