package com.aircargo.auditreportsservice.service;

import com.aircargo.auditreportsservice.entity.*;
import com.aircargo.auditreportsservice.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExportService {

    private final MawbRepository mawbRepository;
    private final BookingRepository bookingRepository;
    private final WarehouseReceiptRepository receiptRepository;
    private final FlightRepository flightRepository;
    private final UldRepository uldRepository;
    private final HawbRepository hawbRepository;
    private final AuditLogRepository auditLogRepository;
    private final AppUserRepository appUserRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExportService(MawbRepository mawbRepository, BookingRepository bookingRepository,
                          WarehouseReceiptRepository receiptRepository, FlightRepository flightRepository,
                          UldRepository uldRepository, HawbRepository hawbRepository,
                          AuditLogRepository auditLogRepository,
                          AppUserRepository appUserRepository) {
        this.mawbRepository = mawbRepository;
        this.bookingRepository = bookingRepository;
        this.receiptRepository = receiptRepository;
        this.flightRepository = flightRepository;
        this.uldRepository = uldRepository;
        this.hawbRepository = hawbRepository;
        this.auditLogRepository = auditLogRepository;
        this.appUserRepository = appUserRepository;
    }

    private Map<UUID, String> buildUserRoleMap() {
        Map<UUID, String> map = new HashMap<>();
        appUserRepository.findAll().forEach(u -> map.put(u.getId(), u.getRole()));
        return map;
    }

    private String userRole(AuditLogEntity log, Map<UUID, String> roleMap) {
        return log.getUserId() != null ? roleMap.getOrDefault(log.getUserId(), "") : "";
    }

    public ByteArrayInputStream export(String type, String format, LocalDate dateFrom, LocalDate dateTo, boolean audit) {
        return exportCsv(type, dateFrom, dateTo, audit);
    }

    public ByteArrayInputStream exportCsvPublic(String type, String format, LocalDate dateFrom, LocalDate dateTo, boolean audit) {
        return exportCsv(type, dateFrom, dateTo, audit);
    }

    public List<Map<String, Object>> exportJson(String type, LocalDate dateFrom, LocalDate dateTo, boolean audit) {
        return switch (type.toUpperCase()) {
            case "MAWBS" -> audit ? auditJson("MAWB", dateFrom, dateTo)
                                  : mawbRepository.findAll().stream().map(this::mawbToMap).collect(Collectors.toList());
            case "BOOKINGS" -> audit ? auditJson("BOOKING", dateFrom, dateTo)
                                     : bookingRepository.findAll().stream().map(this::bookingToMap).collect(Collectors.toList());
            case "RECEIPTS" -> audit ? receiptAuditJson(dateFrom, dateTo)
                                     : receiptRepository.findAll().stream().map(this::receiptToMap).collect(Collectors.toList());
            case "FLIGHTS" -> audit ? auditJson("FLIGHT", dateFrom, dateTo)
                                    : flightRepository.findAll().stream().map(this::flightToMap).collect(Collectors.toList());
            case "ULDS" -> audit ? auditJson("ULD", dateFrom, dateTo)
                                 : uldRepository.findAll().stream().map(this::uldToMap).collect(Collectors.toList());
            case "HAWBS" -> audit ? auditJson("HAWB", dateFrom, dateTo)
                                  : hawbRepository.findAll().stream().map(this::hawbToMap).collect(Collectors.toList());
            default -> List.of(Map.of("error", "Tipo no soportado: " + type));
        };
    }

    private ByteArrayInputStream exportCsv(String type, LocalDate dateFrom, LocalDate dateTo, boolean audit) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        if (audit) {
            switch (type.toUpperCase()) {
                case "MAWBS" -> exportAuditMawbs(pw, dateFrom, dateTo);
                case "BOOKINGS" -> exportAuditBookings(pw, dateFrom, dateTo);
                case "RECEIPTS" -> exportAuditReceipts(pw, dateFrom, dateTo);
                case "FLIGHTS" -> exportAuditFlights(pw, dateFrom, dateTo);
                case "ULDS" -> exportAuditUlds(pw, dateFrom, dateTo);
                case "HAWBS" -> exportAuditHawbs(pw, dateFrom, dateTo);
                default -> pw.println("Tipo no soportado: " + type);
            }
        } else {
            switch (type.toUpperCase()) {
                case "MAWBS" -> exportMawbs(pw, dateFrom, dateTo);
                case "BOOKINGS" -> exportBookings(pw, dateFrom, dateTo);
                case "RECEIPTS" -> exportReceipts(pw, dateFrom, dateTo);
                case "FLIGHTS" -> exportFlights(pw, dateFrom, dateTo);
                case "ULDS" -> exportUlds(pw, dateFrom, dateTo);
                case "HAWBS" -> exportHawbs(pw, dateFrom, dateTo);
                default -> pw.println("Tipo no soportado: " + type);
            }
        }
        pw.flush();
        return new ByteArrayInputStream(out.toByteArray());
    }

    private void exportMawbs(PrintWriter pw, LocalDate dateFrom, LocalDate dateTo) {
        Map<UUID, String> roleMap = buildUserRoleMap();
        Map<String, MawbEntity> byId = mawbRepository.findAll().stream()
                .collect(Collectors.toMap(m -> m.getId().toString(), m -> m));
        pw.println("AWB Number,Shipper,Consignee,Origin,Destination,Pieces,Reported Weight Kg,Chargeable Weight Kg,Status,Created At,Created By,Transaction Type,User Role,Email");
        for (MawbEntity m : byId.values()) {
            pw.println(join(m.getAwbNumber(), m.getShipperName(), m.getConsigneeName(),
                m.getOrigin(), m.getDestination(), String.valueOf(m.getPieces()),
                str(m.getReportedWeightKg()), str(m.getChargeableWeightKg()),
                m.getStatus() != null ? m.getStatus() : "",
                m.getCreatedAt() != null ? m.getCreatedAt().toString() : "",
                "", "", "", ""));
        }
        for (AuditLogEntity log : auditLogRepository.findByEntityTypeOrderByCreatedAtDesc("MAWB")) {
            if (!inDateRange(log, dateFrom, dateTo)) continue;
            if (log.getEntityId() != null && byId.containsKey(log.getEntityId())) continue;
            Map<String, String> d = parseDetails(log.getDetails());
            pw.println(join(d.getOrDefault("awbNumber", ""),
                d.getOrDefault("shipperName", ""), d.getOrDefault("consigneeName", ""),
                d.getOrDefault("origin", ""), d.getOrDefault("destination", ""),
                d.getOrDefault("pieces", ""), d.getOrDefault("reportedWeightKg", ""),
                d.getOrDefault("chargeableWeightKg", ""), d.getOrDefault("status", ""),
                log.getCreatedAt() != null ? log.getCreatedAt().toString() : "",
                log.getFullName(), log.getAction(), userRole(log, roleMap), log.getEmail()));
        }
    }

    private void exportBookings(PrintWriter pw, LocalDate dateFrom, LocalDate dateTo) {
        Map<UUID, String> roleMap = buildUserRoleMap();
        Map<String, BookingEntity> byId = bookingRepository.findAll().stream()
                .collect(Collectors.toMap(b -> b.getId().toString(), b -> b));
        pw.println("ID,AWB Number,Shipper,Destination,Skids,Reserved Kg,Created At,Created By,Transaction Type,User Role,Email");
        for (BookingEntity b : byId.values()) {
            pw.println(join(b.getId().toString(), b.getAwbNumber(), b.getShipperName(),
                b.getDestination(), String.valueOf(b.getSkids()), str(b.getReservedKg()),
                b.getCreatedAt() != null ? b.getCreatedAt().toString() : "",
                "", "", "", ""));
        }
        for (AuditLogEntity log : auditLogRepository.findByEntityTypeOrderByCreatedAtDesc("BOOKING")) {
            if (!inDateRange(log, dateFrom, dateTo)) continue;
            if (log.getEntityId() != null && byId.containsKey(log.getEntityId())) continue;
            Map<String, String> d = parseDetails(log.getDetails());
            pw.println(join(log.getEntityId() != null ? log.getEntityId() : "",
                d.getOrDefault("awbNumber", ""), d.getOrDefault("shipperName", ""),
                d.getOrDefault("destination", ""), d.getOrDefault("skids", ""),
                d.getOrDefault("reservedKg", ""),
                log.getCreatedAt() != null ? log.getCreatedAt().toString() : "",
                log.getFullName(), log.getAction(), userRole(log, roleMap), log.getEmail()));
        }
    }

    private void exportReceipts(PrintWriter pw, LocalDate dateFrom, LocalDate dateTo) {
        Map<UUID, String> roleMap = buildUserRoleMap();
        List<MawbEntity> allMawbs = mawbRepository.findAll();
        Map<UUID, String> mawbNumberMap = allMawbs.stream()
                .collect(Collectors.toMap(MawbEntity::getId, MawbEntity::getAwbNumber, (a, b) -> a));
        Map<String, WarehouseReceiptEntity> byId = receiptRepository.findAll().stream()
                .collect(Collectors.toMap(r -> r.getId().toString(), r -> r));
        pw.println("ID,MAWB,Shipper,Pieces,Actual Kg,Chargeable Kg,Created At,Created By,Transaction Type,User Role,Email");
        for (WarehouseReceiptEntity r : byId.values()) {
            String mawbNum = mawbNumberMap.getOrDefault(r.getMawbId(), "");
            pw.println(join(r.getId().toString(), mawbNum, r.getShipperName(),
                String.valueOf(r.getPieceCount()), str(r.getActualWeightKg()),
                str(r.getChargeableWeightKg()),
                r.getCreatedAt() != null ? r.getCreatedAt().toString() : "",
                "", "", "", ""));
        }
        List<AuditLogEntity> allLogs = new ArrayList<>();
        allLogs.addAll(auditLogRepository.findByEntityTypeOrderByCreatedAtDesc("RECEIPT"));
        allLogs.addAll(auditLogRepository.findByEntityTypeOrderByCreatedAtDesc("RECEIPT_CORRECTION"));
        allLogs.sort(Comparator.comparing(AuditLogEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        for (AuditLogEntity log : allLogs) {
            if (!inDateRange(log, dateFrom, dateTo)) continue;
            if (log.getEntityId() != null && byId.containsKey(log.getEntityId())) continue;
            Map<String, String> d = parseDetails(log.getDetails());
            pw.println(join(log.getEntityId() != null ? log.getEntityId() : "",
                d.getOrDefault("mawb", ""), d.getOrDefault("shipperName", ""),
                d.getOrDefault("pieces", ""), d.getOrDefault("actualKg", ""),
                d.getOrDefault("chargeableKg", ""),
                log.getCreatedAt() != null ? log.getCreatedAt().toString() : "",
                log.getFullName(), log.getAction(), userRole(log, roleMap), log.getEmail()));
        }
    }

    private void exportFlights(PrintWriter pw, LocalDate dateFrom, LocalDate dateTo) {
        Map<UUID, String> roleMap = buildUserRoleMap();
        Map<String, FlightEntity> byId = flightRepository.findAll().stream()
                .collect(Collectors.toMap(f -> f.getId().toString(), f -> f));
        pw.println("Flight Number,Origin,Destination,Flight Date,Aircraft,Status,Created At,Created By,Transaction Type,User Role,Email");
        for (FlightEntity f : byId.values()) {
            pw.println(join(f.getFlightNumber(), f.getOrigin(), f.getDestination(),
                f.getFlightDate() != null ? f.getFlightDate().toString() : "",
                f.getAircraftType() != null ? f.getAircraftType() : "",
                f.getStatus() != null ? f.getStatus() : "",
                f.getCreatedAt() != null ? f.getCreatedAt().toString() : "",
                "", "", "", ""));
        }
        for (AuditLogEntity log : auditLogRepository.findByEntityTypeOrderByCreatedAtDesc("FLIGHT")) {
            if (!inDateRange(log, dateFrom, dateTo)) continue;
            if (log.getEntityId() != null && byId.containsKey(log.getEntityId())) continue;
            Map<String, String> d = parseDetails(log.getDetails());
            pw.println(join(d.getOrDefault("flightNumber", ""),
                d.getOrDefault("origin", ""), d.getOrDefault("destination", ""),
                d.getOrDefault("flightDate", ""), d.getOrDefault("aircraftType", ""),
                d.getOrDefault("status", ""),
                log.getCreatedAt() != null ? log.getCreatedAt().toString() : "",
                log.getFullName(), log.getAction(), userRole(log, roleMap), log.getEmail()));
        }
    }

    private void exportUlds(PrintWriter pw, LocalDate dateFrom, LocalDate dateTo) {
        Map<UUID, String> roleMap = buildUserRoleMap();
        Map<String, UldEntity> byId = uldRepository.findAll().stream()
                .collect(Collectors.toMap(u -> u.getId().toString(), u -> u));
        pw.println("ULD Number,Type,Tare Lbs,Status,Created At,Created By,Transaction Type,User Role,Email");
        for (UldEntity u : byId.values()) {
            pw.println(join(u.getUldNumber(), u.getUldType() != null ? u.getUldType() : "",
                str(u.getTareLbs()), u.getStatus() != null ? u.getStatus() : "",
                u.getCreatedAt() != null ? u.getCreatedAt().toString() : "",
                "", "", "", ""));
        }
        for (AuditLogEntity log : auditLogRepository.findByEntityTypeOrderByCreatedAtDesc("ULD")) {
            if (!inDateRange(log, dateFrom, dateTo)) continue;
            if (log.getEntityId() != null && byId.containsKey(log.getEntityId())) continue;
            Map<String, String> d = parseDetails(log.getDetails());
            pw.println(join(d.getOrDefault("uldNumber", ""),
                d.getOrDefault("uldType", ""), d.getOrDefault("tareLbs", ""),
                d.getOrDefault("status", ""),
                log.getCreatedAt() != null ? log.getCreatedAt().toString() : "",
                log.getFullName(), log.getAction(), userRole(log, roleMap), log.getEmail()));
        }
    }

    private void exportHawbs(PrintWriter pw, LocalDate dateFrom, LocalDate dateTo) {
        Map<UUID, String> roleMap = buildUserRoleMap();
        List<MawbEntity> allMawbs = mawbRepository.findAll();
        Map<UUID, String> mawbNumberMap = allMawbs.stream()
                .collect(Collectors.toMap(MawbEntity::getId, MawbEntity::getAwbNumber, (a, b) -> a));
        Map<String, HawbEntity> byId = hawbRepository.findAll().stream()
                .collect(Collectors.toMap(h -> h.getId().toString(), h -> h));
        pw.println("HAWB Number,MAWB,Consignee,Pieces,Weight Kg,Destination,Created At,Created By,Transaction Type,User Role,Email");
        for (HawbEntity h : byId.values()) {
            String mawbNum = mawbNumberMap.getOrDefault(h.getMawbId(), "");
            pw.println(join(h.getHawbNumber(), mawbNum, h.getConsigneeName(),
                String.valueOf(h.getPieces()), str(h.getWeightKg()), h.getDestination(),
                h.getCreatedAt() != null ? h.getCreatedAt().toString() : "",
                "", "", "", ""));
        }
        for (AuditLogEntity log : auditLogRepository.findByEntityTypeOrderByCreatedAtDesc("HAWB")) {
            if (!inDateRange(log, dateFrom, dateTo)) continue;
            if (log.getEntityId() != null && byId.containsKey(log.getEntityId())) continue;
            Map<String, String> d = parseDetails(log.getDetails());
            pw.println(join(d.getOrDefault("hawbNumber", ""),
                d.getOrDefault("mawb", ""), d.getOrDefault("consigneeName", ""),
                d.getOrDefault("pieces", ""), d.getOrDefault("weightKg", ""),
                d.getOrDefault("destination", ""),
                log.getCreatedAt() != null ? log.getCreatedAt().toString() : "",
                log.getFullName(), log.getAction(), userRole(log, roleMap), log.getEmail()));
        }
    }

    private void exportAuditMawbs(PrintWriter pw, LocalDate dateFrom, LocalDate dateTo) {
        Map<UUID, String> roleMap = buildUserRoleMap();
        Map<String, MawbEntity> byId = mawbRepository.findAll().stream()
                .collect(Collectors.toMap(m -> m.getId().toString(), m -> m));
        pw.println("AWB Number,shipper,consignee,origin,destination,pieces,reported weight kg,chargeable weight kg,status,created at,created by,transaction type,user role,email");
        for (AuditLogEntity log : auditLogRepository.findByEntityTypeOrderByCreatedAtDesc("MAWB")) {
            if (!inDateRange(log, dateFrom, dateTo)) continue;
            MawbEntity m = byId.get(log.getEntityId());
            pw.println(join(m != null ? m.getAwbNumber() : "",
                m != null ? m.getShipperName() : "",
                m != null ? m.getConsigneeName() : "",
                m != null ? m.getOrigin() : "",
                m != null ? m.getDestination() : "",
                m != null ? String.valueOf(m.getPieces()) : "",
                m != null ? str(m.getReportedWeightKg()) : "",
                m != null ? str(m.getChargeableWeightKg()) : "",
                m != null && m.getStatus() != null ? m.getStatus() : "",
                log.getCreatedAt() != null ? log.getCreatedAt().toString() : "",
                log.getFullName(), log.getAction(), userRole(log, roleMap), log.getEmail()));
        }
    }

    private void exportAuditBookings(PrintWriter pw, LocalDate dateFrom, LocalDate dateTo) {
        Map<UUID, String> roleMap = buildUserRoleMap();
        Map<String, BookingEntity> byId = bookingRepository.findAll().stream()
                .collect(Collectors.toMap(b -> b.getId().toString(), b -> b));
        pw.println("ID,AWB Number,shipper,destination,skids,reserved kg,created at,created by,transaction type,user role,email");
        for (AuditLogEntity log : auditLogRepository.findByEntityTypeOrderByCreatedAtDesc("BOOKING")) {
            if (!inDateRange(log, dateFrom, dateTo)) continue;
            BookingEntity b = byId.get(log.getEntityId());
            pw.println(join(b != null ? b.getId().toString() : "",
                b != null ? b.getAwbNumber() : "",
                b != null ? b.getShipperName() : "",
                b != null ? b.getDestination() : "",
                b != null ? String.valueOf(b.getSkids()) : "",
                b != null ? str(b.getReservedKg()) : "",
                log.getCreatedAt() != null ? log.getCreatedAt().toString() : "",
                log.getFullName(), log.getAction(), userRole(log, roleMap), log.getEmail()));
        }
    }

    private void exportAuditReceipts(PrintWriter pw, LocalDate dateFrom, LocalDate dateTo) {
        Map<UUID, String> roleMap = buildUserRoleMap();
        Map<String, MawbEntity> mawbById = mawbRepository.findAll().stream()
                .collect(Collectors.toMap(m -> m.getId().toString(), m -> m));
        Map<String, WarehouseReceiptEntity> byId = receiptRepository.findAll().stream()
                .collect(Collectors.toMap(r -> r.getId().toString(), r -> r));
        pw.println("ID,mawb,shipper,pieces,actual kg,chargeable kg,created at,created by,transaction type,details,user role,email");
        List<AuditLogEntity> allLogs = new ArrayList<>();
        allLogs.addAll(auditLogRepository.findByEntityTypeOrderByCreatedAtDesc("RECEIPT"));
        allLogs.addAll(auditLogRepository.findByEntityTypeOrderByCreatedAtDesc("RECEIPT_CORRECTION"));
        allLogs.sort(Comparator.comparing(AuditLogEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        for (AuditLogEntity log : allLogs) {
            if (!inDateRange(log, dateFrom, dateTo)) continue;
            WarehouseReceiptEntity r = byId.get(log.getEntityId());
            String mawbNum = "";
            if (r != null && r.getMawbId() != null) {
                MawbEntity m = mawbById.get(r.getMawbId().toString());
                if (m != null) mawbNum = m.getAwbNumber();
            }
            pw.println(join(log.getEntityId() != null ? log.getEntityId() : "", mawbNum,
                r != null ? r.getShipperName() : "",
                r != null ? String.valueOf(r.getPieceCount()) : "",
                r != null ? str(r.getActualWeightKg()) : "",
                r != null ? str(r.getChargeableWeightKg()) : "",
                log.getCreatedAt() != null ? log.getCreatedAt().toString() : "",
                log.getFullName(), log.getAction(), log.getDetails(), userRole(log, roleMap), log.getEmail()));
        }
    }

    private void exportAuditFlights(PrintWriter pw, LocalDate dateFrom, LocalDate dateTo) {
        Map<UUID, String> roleMap = buildUserRoleMap();
        Map<String, FlightEntity> byId = flightRepository.findAll().stream()
                .collect(Collectors.toMap(f -> f.getId().toString(), f -> f));
        pw.println("Flight Number,origin,destination,flight date,aircraft,status,created at,created by,transaction type,user role,email");
        for (AuditLogEntity log : auditLogRepository.findByEntityTypeOrderByCreatedAtDesc("FLIGHT")) {
            if (!inDateRange(log, dateFrom, dateTo)) continue;
            FlightEntity f = byId.get(log.getEntityId());
            pw.println(join(f != null ? f.getFlightNumber() : "",
                f != null ? f.getOrigin() : "",
                f != null ? f.getDestination() : "",
                f != null && f.getFlightDate() != null ? f.getFlightDate().toString() : "",
                f != null ? f.getAircraftType() : "",
                f != null && f.getStatus() != null ? f.getStatus() : "",
                log.getCreatedAt() != null ? log.getCreatedAt().toString() : "",
                log.getFullName(), log.getAction(), userRole(log, roleMap), log.getEmail()));
        }
    }

    private void exportAuditUlds(PrintWriter pw, LocalDate dateFrom, LocalDate dateTo) {
        Map<UUID, String> roleMap = buildUserRoleMap();
        Map<String, UldEntity> byId = uldRepository.findAll().stream()
                .collect(Collectors.toMap(u -> u.getId().toString(), u -> u));
        pw.println("ULD Number,type,tare lbs,status,created at,created by,transaction type,user role,email");
        for (AuditLogEntity log : auditLogRepository.findByEntityTypeOrderByCreatedAtDesc("ULD")) {
            if (!inDateRange(log, dateFrom, dateTo)) continue;
            UldEntity u = byId.get(log.getEntityId());
            pw.println(join(u != null ? u.getUldNumber() : "",
                u != null && u.getUldType() != null ? u.getUldType() : "",
                u != null ? str(u.getTareLbs()) : "",
                u != null && u.getStatus() != null ? u.getStatus() : "",
                log.getCreatedAt() != null ? log.getCreatedAt().toString() : "",
                log.getFullName(), log.getAction(), userRole(log, roleMap), log.getEmail()));
        }
    }

    private void exportAuditHawbs(PrintWriter pw, LocalDate dateFrom, LocalDate dateTo) {
        Map<UUID, String> roleMap = buildUserRoleMap();
        Map<String, HawbEntity> byId = hawbRepository.findAll().stream()
                .collect(Collectors.toMap(h -> h.getId().toString(), h -> h));
        Map<String, MawbEntity> mawbById = mawbRepository.findAll().stream()
                .collect(Collectors.toMap(m -> m.getId().toString(), m -> m));
        pw.println("HAWB Number,mawb,consignee,pieces,weight kg,destination,created at,created by,transaction type,user role,email");
        for (AuditLogEntity log : auditLogRepository.findByEntityTypeOrderByCreatedAtDesc("HAWB")) {
            if (!inDateRange(log, dateFrom, dateTo)) continue;
            HawbEntity h = byId.get(log.getEntityId());
            String mawbNum = "";
            if (h != null && h.getMawbId() != null) {
                MawbEntity m = mawbById.get(h.getMawbId().toString());
                if (m != null) mawbNum = m.getAwbNumber();
            }
            pw.println(join(h != null ? h.getHawbNumber() : "", mawbNum,
                h != null ? h.getConsigneeName() : "",
                h != null ? String.valueOf(h.getPieces()) : "",
                h != null ? str(h.getWeightKg()) : "",
                h != null ? h.getDestination() : "",
                log.getCreatedAt() != null ? log.getCreatedAt().toString() : "",
                log.getFullName(), log.getAction(), userRole(log, roleMap), log.getEmail()));
        }
    }

    private Map<String, Object> mawbToMap(MawbEntity m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("awbNumber", m.getAwbNumber());
        map.put("shipper", m.getShipperName());
        map.put("consignee", m.getConsigneeName());
        map.put("origin", m.getOrigin());
        map.put("destination", m.getDestination());
        map.put("pieces", m.getPieces());
        map.put("reportedWeightKg", m.getReportedWeightKg());
        map.put("chargeableWeightKg", m.getChargeableWeightKg());
        map.put("status", m.getStatus() != null ? m.getStatus() : "");
        map.put("createdAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : "");
        return map;
    }

    private Map<String, Object> bookingToMap(BookingEntity b) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", b.getId().toString());
        map.put("awbNumber", b.getAwbNumber());
        map.put("shipper", b.getShipperName());
        map.put("destination", b.getDestination());
        map.put("skids", b.getSkids());
        map.put("reservedKg", b.getReservedKg());
        map.put("createdAt", b.getCreatedAt() != null ? b.getCreatedAt().toString() : "");
        return map;
    }

    private Map<String, Object> receiptToMap(WarehouseReceiptEntity r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", r.getId().toString());
        map.put("mawb", r.getMawbId().toString());
        map.put("shipper", r.getShipperName());
        map.put("pieces", r.getPieceCount());
        map.put("actualKg", r.getActualWeightKg());
        map.put("chargeableKg", r.getChargeableWeightKg());
        map.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "");
        return map;
    }

    private Map<String, Object> flightToMap(FlightEntity f) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("flightNumber", f.getFlightNumber());
        map.put("origin", f.getOrigin());
        map.put("destination", f.getDestination());
        map.put("flightDate", f.getFlightDate() != null ? f.getFlightDate().toString() : "");
        map.put("aircraft", f.getAircraftType() != null ? f.getAircraftType() : "");
        map.put("status", f.getStatus() != null ? f.getStatus() : "");
        map.put("createdAt", f.getCreatedAt() != null ? f.getCreatedAt().toString() : "");
        return map;
    }

    private Map<String, Object> uldToMap(UldEntity u) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("uldNumber", u.getUldNumber());
        map.put("type", u.getUldType() != null ? u.getUldType() : "");
        map.put("tareLbs", u.getTareLbs());
        map.put("status", u.getStatus() != null ? u.getStatus() : "");
        map.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : "");
        return map;
    }

    private Map<String, Object> hawbToMap(HawbEntity h) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("hawbNumber", h.getHawbNumber());
        map.put("mawb", h.getMawbId().toString());
        map.put("consignee", h.getConsigneeName());
        map.put("pieces", h.getPieces());
        map.put("weightKg", h.getWeightKg());
        map.put("destination", h.getDestination());
        map.put("createdAt", h.getCreatedAt() != null ? h.getCreatedAt().toString() : "");
        return map;
    }

    private List<Map<String, Object>> auditJson(String entityType, LocalDate dateFrom, LocalDate dateTo) {
        Map<UUID, String> roleMap = buildUserRoleMap();
        List<Map<String, Object>> result = new ArrayList<>();
        for (AuditLogEntity log : auditLogRepository.findByEntityTypeOrderByCreatedAtDesc(entityType)) {
            if (!inDateRange(log, dateFrom, dateTo)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("entityId", log.getEntityId() != null ? log.getEntityId() : "");
            m.put("transactionType", log.getAction());
            m.put("createdAt", log.getCreatedAt() != null ? log.getCreatedAt().toString() : "");
            m.put("createdBy", log.getFullName());
            m.put("userRole", userRole(log, roleMap));
            m.put("email", log.getEmail());
            result.add(m);
        }
        return result;
    }

    private List<Map<String, Object>> receiptAuditJson(LocalDate dateFrom, LocalDate dateTo) {
        Map<UUID, String> roleMap = buildUserRoleMap();
        List<Map<String, Object>> result = new ArrayList<>();
        List<AuditLogEntity> allLogs = new ArrayList<>();
        allLogs.addAll(auditLogRepository.findByEntityTypeOrderByCreatedAtDesc("RECEIPT"));
        allLogs.addAll(auditLogRepository.findByEntityTypeOrderByCreatedAtDesc("RECEIPT_CORRECTION"));
        allLogs.sort(Comparator.comparing(AuditLogEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        for (AuditLogEntity log : allLogs) {
            if (!inDateRange(log, dateFrom, dateTo)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("entityId", log.getEntityId() != null ? log.getEntityId() : "");
            m.put("transactionType", log.getAction());
            m.put("createdAt", log.getCreatedAt() != null ? log.getCreatedAt().toString() : "");
            m.put("createdBy", log.getFullName());
            m.put("userRole", userRole(log, roleMap));
            m.put("email", log.getEmail());
            result.add(m);
        }
        return result;
    }

    private boolean inDateRange(AuditLogEntity log, LocalDate from, LocalDate to) {
        if (log.getCreatedAt() == null) return true;
        if (from != null && log.getCreatedAt().toLocalDate().isBefore(from)) return false;
        if (to != null && log.getCreatedAt().toLocalDate().isAfter(to)) return false;
        return true;
    }

    private Map<String, String> parseDetails(String details) {
        if (details == null || details.isBlank()) return Map.of();
        try {
            JsonNode node = objectMapper.readTree(details);
            Map<String, String> map = new HashMap<>();
            if (node.isObject()) {
                node.fields().forEachRemaining(e ->
                    map.put(e.getKey(), e.getValue() != null && e.getValue().isValueNode() ? e.getValue().asText() : ""));
            }
            return map;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String str(Object o) {
        return o != null ? o.toString() : "";
    }

    private String join(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            String f = fields[i] != null ? fields[i] : "";
            if (f.contains(",") || f.contains("\"") || f.contains("\n")) {
                f = "\"" + f.replace("\"", "\"\"") + "\"";
            }
            sb.append(f);
            if (i < fields.length - 1) sb.append(",");
        }
        return sb.toString();
    }
}
