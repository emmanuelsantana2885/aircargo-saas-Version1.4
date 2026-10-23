package com.aircargo.auditreportsservice.controller;

import com.aircargo.auditreportsservice.service.BiService;
import com.aircargo.auditreportsservice.service.ExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final BiService biService;
    private final ExportService exportService;

    public ReportController(BiService biService, ExportService exportService) {
        this.biService = biService;
        this.exportService = exportService;
    }

    private ResponseEntity<?> streamResponse(ByteArrayInputStream bais, String ext, String mime, String filename) {
        byte[] bytes = bais.readAllBytes();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename + "." + ext)
                .contentType(MediaType.valueOf(mime))
                .body(bytes);
    }

    @GetMapping("/daily")
    public ResponseEntity<?> getDailyReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "json") String format) {
        LocalDate reportDate = date != null ? date : LocalDate.now();
        if ("csv".equalsIgnoreCase(format) || "xlsx".equalsIgnoreCase(format)) {
            ByteArrayInputStream data = exportService.export("flights", format, reportDate, reportDate, false);
            String ext = "xlsx".equalsIgnoreCase(format) ? "xlsx" : "csv";
            String mime = "xlsx".equalsIgnoreCase(format) ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : "text/csv";
            return streamResponse(data, ext, mime, "daily-report");
        }
        return ResponseEntity.ok(biService.getDaily(reportDate, reportDate));
    }

    @GetMapping("/weekly")
    public ResponseEntity<?> getWeeklyReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestParam(defaultValue = "json") String format) {
        LocalDate start = weekStart != null ? weekStart : LocalDate.now().minusWeeks(1);
        LocalDate end = start.plusDays(6);
        if ("csv".equalsIgnoreCase(format) || "xlsx".equalsIgnoreCase(format)) {
            ByteArrayInputStream data = exportService.export("flights", format, start, end, false);
            String ext = "xlsx".equalsIgnoreCase(format) ? "xlsx" : "csv";
            String mime = "xlsx".equalsIgnoreCase(format) ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : "text/csv";
            return streamResponse(data, ext, mime, "weekly-report");
        }
        return ResponseEntity.ok(biService.getDaily(start, end));
    }

    @GetMapping("/flights")
    public ResponseEntity<?> getFlightsReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "json") String format) {
        if ("csv".equalsIgnoreCase(format) || "xlsx".equalsIgnoreCase(format)) {
            ByteArrayInputStream data = exportService.export("flights", format, dateFrom, dateTo, false);
            String ext = "xlsx".equalsIgnoreCase(format) ? "xlsx" : "csv";
            String mime = "xlsx".equalsIgnoreCase(format) ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : "text/csv";
            return streamResponse(data, ext, mime, "flights-report");
        }
        return ResponseEntity.ok(biService.getFlights(dateFrom, dateTo));
    }

    @GetMapping("/by-location")
    public ResponseEntity<?> getByLocationReport(@RequestParam(defaultValue = "json") String format) {
        List<Map<String, Object>> data = biService.getByLocation();
        if ("csv".equalsIgnoreCase(format)) {
            StringBuilder csv = new StringBuilder();
            if (!data.isEmpty()) {
                StringJoiner header = new StringJoiner(",");
                data.get(0).keySet().forEach(header::add);
                csv.append(header).append("\n");
            }
            for (Map<String, Object> row : data) {
                StringJoiner line = new StringJoiner(",");
                row.values().forEach(v -> line.add(v != null ? v.toString() : ""));
                csv.append(line).append("\n");
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=location-report.csv")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(csv.toString().getBytes());
        }
        return ResponseEntity.ok(data);
    }
}
