package com.aircargo.auditreportsservice.service;

import com.aircargo.auditreportsservice.entity.*;
import com.aircargo.auditreportsservice.repository.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BiService {

    private final FlightRepository flightRepository;
    private final BookingRepository bookingRepository;
    private final MawbRepository mawbRepository;
    private final UldRepository uldRepository;
    private final UldAwbRepository uldAwbRepository;
    private final WarehouseReceiptRepository receiptRepository;
    private final DuaRecordRepository duaRecordRepository;
    private final AirlineRepository airlineRepository;

    public BiService(FlightRepository flightRepository,
                     BookingRepository bookingRepository,
                     MawbRepository mawbRepository,
                     UldRepository uldRepository,
                     UldAwbRepository uldAwbRepository,
                     WarehouseReceiptRepository receiptRepository,
                     DuaRecordRepository duaRecordRepository,
                     AirlineRepository airlineRepository) {
        this.flightRepository = flightRepository;
        this.bookingRepository = bookingRepository;
        this.mawbRepository = mawbRepository;
        this.uldRepository = uldRepository;
        this.uldAwbRepository = uldAwbRepository;
        this.receiptRepository = receiptRepository;
        this.duaRecordRepository = duaRecordRepository;
        this.airlineRepository = airlineRepository;
    }

    @Cacheable(value = "bi", key = "{#root.methodName, #dateFrom, #dateTo}")
    public List<Map<String, Object>> getFlights(LocalDate dateFrom, LocalDate dateTo) {
        List<FlightEntity> flights = flightRepository.findAll();
        if (dateFrom != null) {
            flights = flights.stream()
                    .filter(f -> !f.getFlightDate().isBefore(dateFrom))
                    .collect(Collectors.toList());
        }
        if (dateTo != null) {
            flights = flights.stream()
                    .filter(f -> !f.getFlightDate().isAfter(dateTo))
                    .collect(Collectors.toList());
        }

        Map<UUID, AirlineEntity> airlineMap = airlineRepository.findAll().stream()
                .collect(Collectors.toMap(AirlineEntity::getId, a -> a));

        return flights.stream().map(f -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("flightId", f.getId());
            row.put("flightNumber", f.getFlightNumber());

            AirlineEntity airline = airlineMap.get(f.getAirlineId());
            row.put("airlineCode", airline != null ? airline.getCode() : null);
            row.put("airlineName", airline != null ? airline.getName() : null);

            row.put("origin", f.getOrigin());
            row.put("destination", f.getDestination());
            row.put("flightDate", f.getFlightDate() != null ? f.getFlightDate().toString() : null);
            row.put("aircraftType", f.getAircraftType());
            row.put("aircraftReg", f.getAircraftReg());
            row.put("status", f.getStatus());
            row.put("maxPayloadKg", f.getMaxPayloadKg());

            List<UldEntity> ulds = uldRepository.findByFlightId(f.getId());
            row.put("totalUlds", ulds.size());

            BigDecimal totalGrossLbs = ulds.stream()
                    .map(u -> u.getGrossWeightLbs() != null ? u.getGrossWeightLbs() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            row.put("totalGrossWeightLbs", totalGrossLbs);

            BigDecimal totalNetLbs = ulds.stream()
                    .map(u -> u.getNetWeightLbs() != null ? u.getNetWeightLbs() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            row.put("totalNetWeightLbs", totalNetLbs);

            List<BookingEntity> bookings = bookingRepository.findByFlightId(f.getId());
            row.put("bookingsCount", bookings.size());

            int totalPieces = uldAwbRepository.findByUldIdIn(
                    ulds.stream().map(UldEntity::getId).collect(Collectors.toList())
            ).stream()
                    .mapToInt(a -> a.getPieces() != null ? a.getPieces() : 0)
                    .sum();
            row.put("totalPieces", totalPieces);

            return row;
        }).collect(Collectors.toList());
    }

    @Cacheable(value = "bi", key = "{#root.methodName, #dateFrom, #dateTo}")
    public List<Map<String, Object>> getBookings(LocalDate dateFrom, LocalDate dateTo) {
        List<BookingEntity> bookings = bookingRepository.findAll();
        List<FlightEntity> allFlights = flightRepository.findAll();
        Map<UUID, FlightEntity> flightMap = allFlights.stream()
                .collect(Collectors.toMap(FlightEntity::getId, f -> f));
        Map<UUID, AirlineEntity> airlineMap = airlineRepository.findAll().stream()
                .collect(Collectors.toMap(AirlineEntity::getId, a -> a));
        Map<UUID, MawbEntity> mawbMap = mawbRepository.findAll().stream()
                .collect(Collectors.toMap(MawbEntity::getId, m -> m));

        if (dateFrom != null || dateTo != null) {
            bookings = bookings.stream()
                    .filter(b -> {
                        FlightEntity flight = flightMap.get(b.getFlightId());
                        if (flight == null) return false;
                        LocalDate bDate = flight.getFlightDate();
                        if (bDate == null) return false;
                        if (dateFrom != null && bDate.isBefore(dateFrom)) return false;
                        if (dateTo != null && bDate.isAfter(dateTo)) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
        }

        return bookings.stream().map(b -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("bookingId", b.getId());
            row.put("awbNumber", b.getAwbNumber());
            row.put("clientName", b.getClientName());
            row.put("shipperName", b.getShipperName());
            row.put("consignee", b.getCnee());

            AirlineEntity airline = airlineMap.get(b.getAirlineId());
            row.put("airlineCode", airline != null ? airline.getCode() : null);
            row.put("airlineName", airline != null ? airline.getName() : null);

            FlightEntity flight = flightMap.get(b.getFlightId());
            row.put("flightNumber", flight != null ? flight.getFlightNumber() : null);
            row.put("flightDate", flight != null && flight.getFlightDate() != null
                    ? flight.getFlightDate().toString() : null);

            row.put("destination", b.getDestination());
            row.put("skids", b.getSkids());
            row.put("units", b.getUnits());
            row.put("reservedKg", b.getReservedKg());
            row.put("confirmedKg", b.getConfirmedKg());
            row.put("receivedKg", b.getReceivedKg());
            row.put("fulfillmentPct", b.getFulfillmentPct());
            row.put("commodityType", b.getCommodityType());
            row.put("priority", b.getPriority());
            row.put("isConfirmed", b.getIsConfirmed());

            MawbEntity mawb = mawbMap.get(b.getMawbId());
            row.put("mawbStatus", mawb != null ? mawb.getStatus() : null);

            row.put("createdAt", b.getCreatedAt() != null ? b.getCreatedAt().toString() : null);
            return row;
        }).collect(Collectors.toList());
    }

    @Cacheable(value = "bi", key = "{#root.methodName}")
    public List<Map<String, Object>> getMawbs() {
        List<MawbEntity> mawbs = mawbRepository.findAll();
        Map<UUID, AirlineEntity> airlineMap = airlineRepository.findAll().stream()
                .collect(Collectors.toMap(AirlineEntity::getId, a -> a));
        Map<UUID, FlightEntity> flightMap = flightRepository.findAll().stream()
                .collect(Collectors.toMap(FlightEntity::getId, f -> f));

        return mawbs.stream().map(m -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("mawbId", m.getId());
            row.put("awbNumber", m.getAwbNumber());

            AirlineEntity airline = airlineMap.get(m.getAirlineId());
            row.put("airlineCode", airline != null ? airline.getCode() : null);
            row.put("airlineName", airline != null ? airline.getName() : null);

            FlightEntity flight = flightMap.get(m.getFlightId());
            row.put("flightNumber", flight != null ? flight.getFlightNumber() : null);
            row.put("flightDate", flight != null && flight.getFlightDate() != null
                    ? flight.getFlightDate().toString() : null);

            row.put("shipperName", m.getShipperName());
            row.put("consigneeName", m.getConsigneeName());
            row.put("origin", m.getOrigin());
            row.put("destination", m.getDestination());
            row.put("pieces", m.getPieces());
            row.put("reportedWeightKg", m.getReportedWeightKg());
            row.put("chargeableWeightKg", m.getChargeableWeightKg());
            row.put("commodityType", m.getCommodityType());
            row.put("status", m.getStatus());
            row.put("cashOnly", m.getCashOnly());
            row.put("preBuilt", m.getPreBuilt());

            List<UldAwbEntity> uldAwbs = uldAwbRepository.findByMawbId(m.getId());
            row.put("uldCount", uldAwbs.stream()
                    .map(UldAwbEntity::getUldId)
                    .filter(Objects::nonNull)
                    .distinct().count());

            int dispatchedPieces = uldAwbs.stream()
                    .mapToInt(a -> a.getPieces() != null ? a.getPieces() : 0)
                    .sum();
            row.put("dispatchedPieces", dispatchedPieces);

            boolean hasReceipt = receiptRepository.findAll().stream()
                    .anyMatch(r -> r.getMawbId().equals(m.getId()) && !Boolean.TRUE.equals(r.getSuperseded()));
            row.put("hasReceipt", hasReceipt);
            row.put("hasDua", duaRecordRepository.existsByMawbId(m.getId()));
            row.put("createdAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
            row.put("updatedAt", m.getUpdatedAt() != null ? m.getUpdatedAt().toString() : null);
            return row;
        }).collect(Collectors.toList());
    }

    @Cacheable(value = "bi", key = "{#root.methodName}")
    public List<Map<String, Object>> getReceipts() {
        List<WarehouseReceiptEntity> receipts = receiptRepository.findAll();
        Map<UUID, AirlineEntity> airlineMap = airlineRepository.findAll().stream()
                .collect(Collectors.toMap(AirlineEntity::getId, a -> a));

        return receipts.stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("receiptId", r.getId());

            AirlineEntity airline = airlineMap.get(r.getAirlineId());
            row.put("airlineCode", airline != null ? airline.getCode() : null);
            row.put("airlineName", airline != null ? airline.getName() : null);

            row.put("shipperName", r.getShipperName());
            row.put("consigneeName", r.getConsigneeName());
            row.put("origin", r.getOrigin());
            row.put("destination", r.getDestination());
            row.put("pieceCount", r.getPieceCount());
            row.put("awbReportedPieces", r.getAwbReportedPieces());
            row.put("actualWeightKg", r.getActualWeightKg());
            row.put("chargeableWeightKg", r.getChargeableWeightKg());
            row.put("actualWeightLbs", r.getActualWeightLbs());
            row.put("chargeableWeightLbs", r.getChargeableWeightLbs());
            row.put("cashOnly", r.getCashOnly());
            row.put("bookedInAcoms", r.getBookedInAcoms());
            row.put("docsProvided", r.getDocsProvided());
            row.put("customsCompleted", r.getCustomsCompleted());
            row.put("preBuilt", r.getPreBuilt());
            row.put("createdByName", r.getCreatedByName());
            row.put("receiptDate", r.getReceiptDate() != null ? r.getReceiptDate().toString() : null);
            row.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
            return row;
        }).collect(Collectors.toList());
    }

    @Cacheable(value = "bi", key = "{#root.methodName}")
    public List<Map<String, Object>> getUlds() {
        List<UldEntity> ulds = uldRepository.findAll();
        Map<UUID, AirlineEntity> airlineMap = airlineRepository.findAll().stream()
                .collect(Collectors.toMap(AirlineEntity::getId, a -> a));
        Map<UUID, FlightEntity> flightMap = flightRepository.findAll().stream()
                .collect(Collectors.toMap(FlightEntity::getId, f -> f));

        return ulds.stream().map(u -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("uldId", u.getId());
            row.put("uldNumber", u.getUldNumber());
            row.put("uldType", u.getUldType());

            AirlineEntity airline = airlineMap.get(u.getAirlineId());
            row.put("airlineCode", airline != null ? airline.getCode() : null);
            row.put("airlineName", airline != null ? airline.getName() : null);

            FlightEntity flight = flightMap.get(u.getFlightId());
            row.put("flightNumber", flight != null ? flight.getFlightNumber() : null);
            row.put("flightDate", flight != null && flight.getFlightDate() != null
                    ? flight.getFlightDate().toString() : null);

            row.put("position", u.getPosition());
            row.put("status", u.getStatus());
            row.put("tareLbs", u.getTareLbs());
            row.put("grossWeightLbs", u.getGrossWeightLbs());
            row.put("netWeightLbs", u.getNetWeightLbs());
            row.put("tareKg", u.getTareKg());
            row.put("grossWeightKg", u.getGrossWeightKg());
            row.put("netWeightKg", u.getNetWeightKg());
            row.put("sealNumber", u.getSealNumber());

            List<UldAwbEntity> awbs = uldAwbRepository.findByUldId(u.getId());
            row.put("awbCount", awbs.size());
            row.put("totalPieces", awbs.stream()
                    .mapToInt(a -> a.getPieces() != null ? a.getPieces() : 0)
                    .sum());
            row.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : null);
            return row;
        }).collect(Collectors.toList());
    }

    @Cacheable(value = "bi", key = "{#root.methodName}")
    public Map<String, Object> getDashboard() {
        Map<String, Object> kpis = new LinkedHashMap<>();

        List<FlightEntity> flights = flightRepository.findAll();
        List<BookingEntity> bookings = bookingRepository.findAll();
        List<MawbEntity> mawbs = mawbRepository.findAll();
        List<UldEntity> ulds = uldRepository.findAll();
        List<WarehouseReceiptEntity> receipts = receiptRepository.findAll();

        kpis.put("totalFlights", flights.size());
        kpis.put("flightsScheduled", flights.stream()
                .filter(f -> "SCHEDULED".equals(f.getStatus())).count());
        kpis.put("flightsBoarding", flights.stream()
                .filter(f -> "BOARDING".equals(f.getStatus())).count());
        kpis.put("flightsDeparted", flights.stream()
                .filter(f -> "DEPARTED".equals(f.getStatus())).count());
        kpis.put("flightsArrived", flights.stream()
                .filter(f -> "ARRIVED".equals(f.getStatus())).count());

        kpis.put("totalBookings", bookings.size());
        kpis.put("totalReservedKg", bookings.stream()
                .map(b -> b.getReservedKg() != null ? b.getReservedKg() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        kpis.put("totalReceivedKg", bookings.stream()
                .map(b -> b.getReceivedKg() != null ? b.getReceivedKg() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        kpis.put("totalMawbs", mawbs.size());
        kpis.put("mawbsBooked", mawbs.stream()
                .filter(m -> "BOOKED".equals(m.getStatus())).count());
        kpis.put("mawbsReceived", mawbs.stream()
                .filter(m -> "RECEIVED".equals(m.getStatus())).count());
        kpis.put("mawbsManifested", mawbs.stream()
                .filter(m -> "MANIFESTED".equals(m.getStatus())).count());
        kpis.put("mawbsDeparted", mawbs.stream()
                .filter(m -> "DEPARTED".equals(m.getStatus())).count());

        kpis.put("totalPieces", mawbs.stream()
                .mapToInt(m -> m.getPieces() != null ? m.getPieces() : 0)
                .sum());
        kpis.put("totalReportedWeightKg", mawbs.stream()
                .map(m -> m.getReportedWeightKg() != null ? m.getReportedWeightKg() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        kpis.put("totalUlds", ulds.size());
        kpis.put("uldsOpen", ulds.stream()
                .filter(u -> "OPEN".equals(u.getStatus())).count());
        kpis.put("uldsBuilt", ulds.stream()
                .filter(u -> "BUILT".equals(u.getStatus())).count());
        kpis.put("uldsSealed", ulds.stream()
                .filter(u -> "SEALED".equals(u.getStatus())).count());
        kpis.put("uldsLoaded", ulds.stream()
                .filter(u -> "LOADED".equals(u.getStatus())).count());

        kpis.put("totalReceipts", receipts.size());

        BigDecimal avgFulfillment = bookings.stream()
                .filter(b -> b.getFulfillmentPct() != null)
                .map(BookingEntity::getFulfillmentPct)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long bookingsWithFulfillment = bookings.stream()
                .filter(b -> b.getFulfillmentPct() != null).count();
        if (bookingsWithFulfillment > 0) {
            kpis.put("avgFulfillmentPct", avgFulfillment
                    .divide(BigDecimal.valueOf(bookingsWithFulfillment), 4, RoundingMode.HALF_UP));
        } else {
            kpis.put("avgFulfillmentPct", BigDecimal.ZERO);
        }

        return kpis;
    }

    @Cacheable(value = "bi", key = "{#root.methodName, #dateFrom, #dateTo}")
    public List<Map<String, Object>> getDaily(LocalDate dateFrom, LocalDate dateTo) {
        List<FlightEntity> flights = flightRepository.findAll();
        List<MawbEntity> mawbs = mawbRepository.findAll();
        List<WarehouseReceiptEntity> receipts = receiptRepository.findAll();

        Set<LocalDate> allDates = new TreeSet<>();
        flights.stream().map(FlightEntity::getFlightDate).filter(Objects::nonNull).forEach(allDates::add);
        mawbs.stream().filter(m -> m.getCreatedAt() != null)
                .map(m -> m.getCreatedAt().toLocalDate()).forEach(allDates::add);
        receipts.stream().filter(r -> r.getCreatedAt() != null)
                .map(r -> r.getCreatedAt().toLocalDate()).forEach(allDates::add);

        if (dateFrom != null) allDates = allDates.stream().filter(d -> !d.isBefore(dateFrom)).collect(Collectors.toCollection(TreeSet::new));
        if (dateTo != null) allDates = allDates.stream().filter(d -> !d.isAfter(dateTo)).collect(Collectors.toCollection(TreeSet::new));

        return allDates.stream().map(date -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", date.toString());

            long flightsCount = flights.stream()
                    .filter(f -> date.equals(f.getFlightDate())).count();
            row.put("flightsCount", flightsCount);

            long mawbsCount = mawbs.stream()
                    .filter(m -> m.getCreatedAt() != null && date.equals(m.getCreatedAt().toLocalDate()))
                    .count();
            row.put("mawbsCount", mawbsCount);

            int piecesTotal = mawbs.stream()
                    .filter(m -> m.getCreatedAt() != null && date.equals(m.getCreatedAt().toLocalDate()))
                    .mapToInt(m -> m.getPieces() != null ? m.getPieces() : 0)
                    .sum();
            row.put("piecesTotal", piecesTotal);

            BigDecimal weightTotal = mawbs.stream()
                    .filter(m -> m.getCreatedAt() != null && date.equals(m.getCreatedAt().toLocalDate()))
                    .map(m -> m.getReportedWeightKg() != null ? m.getReportedWeightKg() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            row.put("weightTotalKg", weightTotal);

            long receiptsCount = receipts.stream()
                    .filter(r -> r.getCreatedAt() != null && date.equals(r.getCreatedAt().toLocalDate()))
                    .count();
            row.put("receiptsCount", receiptsCount);

            return row;
        }).collect(Collectors.toList());
    }

    @Cacheable(value = "bi", key = "{#root.methodName, #dateFrom, #dateTo}")
    public Map<String, Object> getSummary(LocalDate dateFrom, LocalDate dateTo) {
        Map<String, Object> summary = new LinkedHashMap<>();

        List<FlightEntity> flights = flightRepository.findAll();
        if (dateFrom != null) flights = flights.stream()
                .filter(f -> f.getFlightDate() != null && !f.getFlightDate().isBefore(dateFrom))
                .collect(Collectors.toList());
        if (dateTo != null) flights = flights.stream()
                .filter(f -> f.getFlightDate() != null && !f.getFlightDate().isAfter(dateTo))
                .collect(Collectors.toList());

        List<BookingEntity> bookings = bookingRepository.findAll();
        if (dateFrom != null || dateTo != null) {
            Map<UUID, FlightEntity> flightMap = flightRepository.findAll().stream()
                    .collect(Collectors.toMap(FlightEntity::getId, f -> f));
            bookings = bookings.stream()
                    .filter(b -> {
                        FlightEntity f = flightMap.get(b.getFlightId());
                        if (f == null || f.getFlightDate() == null) return false;
                        if (dateFrom != null && f.getFlightDate().isBefore(dateFrom)) return false;
                        if (dateTo != null && f.getFlightDate().isAfter(dateTo)) return false;
                        return true;
                    })
                    .collect(Collectors.toList());
        }

        List<MawbEntity> mawbs = mawbRepository.findAll();
        if (dateFrom != null) mawbs = mawbs.stream()
                .filter(m -> m.getCreatedAt() != null && !m.getCreatedAt().toLocalDate().isBefore(dateFrom))
                .collect(Collectors.toList());
        if (dateTo != null) mawbs = mawbs.stream()
                .filter(m -> m.getCreatedAt() != null && !m.getCreatedAt().toLocalDate().isAfter(dateTo))
                .collect(Collectors.toList());

        List<WarehouseReceiptEntity> receipts = receiptRepository.findBySupersededFalse();
        if (dateFrom != null) receipts = receipts.stream()
                .filter(r -> r.getCreatedAt() != null && !r.getCreatedAt().toLocalDate().isBefore(dateFrom))
                .collect(Collectors.toList());
        if (dateTo != null) receipts = receipts.stream()
                .filter(r -> r.getCreatedAt() != null && !r.getCreatedAt().toLocalDate().isAfter(dateTo))
                .collect(Collectors.toList());

        List<UldEntity> ulds = uldRepository.findAll();

        summary.put("totalFlights", flights.size());
        summary.put("totalBookings", bookings.size());
        summary.put("totalMawbs", mawbs.size());
        summary.put("totalReceipts", receipts.size());
        summary.put("totalUlds", ulds.size());
        summary.put("totalPieces", mawbs.stream()
                .mapToInt(m -> m.getPieces() != null ? m.getPieces() : 0)
                .sum());
        summary.put("totalWeightKg", mawbs.stream()
                .map(m -> m.getReportedWeightKg() != null ? m.getReportedWeightKg() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        long bookingsWithFulfillment = bookings.stream()
                .filter(b -> b.getFulfillmentPct() != null).count();
        if (bookingsWithFulfillment > 0) {
            BigDecimal totalFulfillment = bookings.stream()
                    .filter(b -> b.getFulfillmentPct() != null)
                    .map(BookingEntity::getFulfillmentPct)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            summary.put("avgFulfillmentPct", totalFulfillment
                    .divide(BigDecimal.valueOf(bookingsWithFulfillment), 4, RoundingMode.HALF_UP));
        } else {
            summary.put("avgFulfillmentPct", BigDecimal.ZERO);
        }

        return summary;
    }

    @Cacheable(value = "bi", key = "{#root.methodName}")
    public List<Map<String, Object>> getByLocation() {
        List<FlightEntity> flights = flightRepository.findAll();
        List<BookingEntity> allBookings = bookingRepository.findAll();
        List<MawbEntity> allMawbs = mawbRepository.findAll();
        List<UldEntity> allUlds = uldRepository.findAll();

        Map<UUID, List<BookingEntity>> bookingsPerFlight = allBookings.stream()
                .filter(b -> b.getFlightId() != null)
                .collect(Collectors.groupingBy(BookingEntity::getFlightId));

        Map<UUID, List<MawbEntity>> mawbsPerFlight = allMawbs.stream()
                .filter(m -> m.getFlightId() != null)
                .collect(Collectors.groupingBy(MawbEntity::getFlightId));

        Map<UUID, List<UldEntity>> uldsPerFlight = allUlds.stream()
                .filter(u -> u.getFlightId() != null)
                .collect(Collectors.groupingBy(UldEntity::getFlightId));

        Map<String, List<FlightEntity>> flightsByOrigin = flights.stream()
                .filter(f -> f.getOrigin() != null)
                .collect(Collectors.groupingBy(FlightEntity::getOrigin));

        List<Map<String, Object>> result = new ArrayList<>();
        flightsByOrigin.forEach((origin, originFlights) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("origin", origin);
            row.put("flightsCount", originFlights.size());

            long totalBookings = originFlights.stream()
                    .mapToLong(f -> bookingsPerFlight.getOrDefault(f.getId(), List.of()).size())
                    .sum();
            row.put("totalBookings", totalBookings);

            int totalPieces = originFlights.stream()
                    .flatMapToInt(f -> mawbsPerFlight.getOrDefault(f.getId(), List.of()).stream()
                            .mapToInt(m -> m.getPieces() != null ? m.getPieces() : 0))
                    .sum();
            row.put("totalPieces", totalPieces);

            BigDecimal totalWeightKg = originFlights.stream()
                    .flatMap(f -> mawbsPerFlight.getOrDefault(f.getId(), List.of()).stream())
                    .map(m -> m.getReportedWeightKg() != null ? m.getReportedWeightKg() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            row.put("totalWeightKg", totalWeightKg);

            long totalUlds = originFlights.stream()
                    .mapToLong(f -> uldsPerFlight.getOrDefault(f.getId(), List.of()).size())
                    .sum();
            row.put("totalUlds", totalUlds);

            result.add(row);
        });

        return result;
    }

    @Cacheable(value = "bi", key = "{#root.methodName, #dateFrom, #dateTo}")
    public List<Map<String, Object>> getTimeline(LocalDate dateFrom, LocalDate dateTo) {
        List<FlightEntity> flights = flightRepository.findAll();
        List<MawbEntity> mawbs = mawbRepository.findAll();
        List<WarehouseReceiptEntity> receipts = receiptRepository.findBySupersededFalse();

        Set<LocalDate> allDates = new TreeSet<>();
        flights.stream().map(FlightEntity::getFlightDate).filter(Objects::nonNull).forEach(allDates::add);
        mawbs.stream().filter(m -> m.getCreatedAt() != null)
                .map(m -> m.getCreatedAt().toLocalDate()).forEach(allDates::add);
        receipts.stream().filter(r -> r.getCreatedAt() != null)
                .map(r -> r.getCreatedAt().toLocalDate()).forEach(allDates::add);

        if (dateFrom != null) allDates = allDates.stream().filter(d -> !d.isBefore(dateFrom))
                .collect(Collectors.toCollection(TreeSet::new));
        if (dateTo != null) allDates = allDates.stream().filter(d -> !d.isAfter(dateTo))
                .collect(Collectors.toCollection(TreeSet::new));

        List<LocalDate> dates = new ArrayList<>(allDates);

        return dates.stream().map(date -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", date.toString());
            row.put("flightsCount", flights.stream()
                    .filter(f -> date.equals(f.getFlightDate())).count());
            row.put("mawbsCount", mawbs.stream()
                    .filter(m -> m.getCreatedAt() != null && date.equals(m.getCreatedAt().toLocalDate()))
                    .count());
            row.put("receiptsCount", receipts.stream()
                    .filter(r -> r.getCreatedAt() != null && date.equals(r.getCreatedAt().toLocalDate()))
                    .count());
            row.put("totalPieces", mawbs.stream()
                    .filter(m -> m.getCreatedAt() != null && date.equals(m.getCreatedAt().toLocalDate()))
                    .mapToInt(m -> m.getPieces() != null ? m.getPieces() : 0)
                    .sum());
            row.put("totalWeightKg", mawbs.stream()
                    .filter(m -> m.getCreatedAt() != null && date.equals(m.getCreatedAt().toLocalDate()))
                    .map(m -> m.getReportedWeightKg() != null ? m.getReportedWeightKg() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
            return row;
        }).collect(Collectors.toList());
    }

    @Cacheable(value = "bi", key = "{#root.methodName, #limit}")
    public List<Map<String, Object>> getTopMawbs(int limit) {
        List<MawbEntity> mawbs = mawbRepository.findAll();
        Map<UUID, FlightEntity> flightMap = flightRepository.findAll().stream()
                .collect(Collectors.toMap(FlightEntity::getId, f -> f));

        return mawbs.stream()
                .sorted(Comparator.comparing(
                        (MawbEntity m) -> m.getReportedWeightKg() != null ? m.getReportedWeightKg() : BigDecimal.ZERO)
                        .reversed())
                .limit(limit)
                .map(m -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("mawbId", m.getId());
                    row.put("awbNumber", m.getAwbNumber());
                    row.put("shipperName", m.getShipperName());
                    row.put("destination", m.getDestination());
                    row.put("pieces", m.getPieces());
                    row.put("reportedWeightKg", m.getReportedWeightKg());
                    row.put("status", m.getStatus());

                    FlightEntity flight = flightMap.get(m.getFlightId());
                    row.put("flightNumber", flight != null ? flight.getFlightNumber() : null);
                    return row;
                })
                .collect(Collectors.toList());
    }

    @Cacheable(value = "bi", key = "{#root.methodName, #dateFrom, #dateTo}")
    public List<Map<String, Object>> getFlightPerformance(LocalDate dateFrom, LocalDate dateTo) {
        List<FlightEntity> flights = flightRepository.findAll();
        if (dateFrom != null) flights = flights.stream()
                .filter(f -> f.getFlightDate() != null && !f.getFlightDate().isBefore(dateFrom))
                .collect(Collectors.toList());
        if (dateTo != null) flights = flights.stream()
                .filter(f -> f.getFlightDate() != null && !f.getFlightDate().isAfter(dateTo))
                .collect(Collectors.toList());

        flights.sort(Comparator.comparing(
                (FlightEntity f) -> f.getFlightDate() != null ? f.getFlightDate() : LocalDate.MIN).reversed());

        Map<UUID, List<BookingEntity>> bookingsByFlight = bookingRepository.findAll().stream()
                .filter(b -> b.getFlightId() != null)
                .collect(Collectors.groupingBy(BookingEntity::getFlightId));

        return flights.stream().map(f -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("flightNumber", f.getFlightNumber());
            row.put("flightDate", f.getFlightDate() != null ? f.getFlightDate().toString() : null);
            row.put("origin", f.getOrigin());
            row.put("destination", f.getDestination());
            row.put("aircraftType", f.getAircraftType());
            row.put("status", f.getStatus());

            List<BookingEntity> bookings = bookingsByFlight.getOrDefault(f.getId(), List.of());
            row.put("bookingsCount", bookings.size());
            row.put("bookedPieces", bookings.stream()
                    .mapToInt(b -> b.getSkids() != null ? b.getSkids() : 0)
                    .sum());

            List<BookingEntity> receivedBookings = bookings.stream()
                    .filter(b -> b.getReceivedKg() != null && b.getReceivedKg().compareTo(BigDecimal.ZERO) > 0)
                    .collect(Collectors.toList());
            row.put("receivedPieces", receivedBookings.size());

            List<UldEntity> ulds = uldRepository.findByFlightId(f.getId());
            row.put("uldsCount", ulds.size());

            List<UldAwbEntity> uldAwbs = uldAwbRepository.findByUldIdIn(
                    ulds.stream().map(UldEntity::getId).collect(Collectors.toList()));
            int dispatchedPieces = uldAwbs.stream()
                    .mapToInt(a -> a.getPieces() != null ? a.getPieces() : 0)
                    .sum();
            row.put("dispatchedPieces", dispatchedPieces);

            if (f.getMaxPayloadKg() != null && f.getMaxPayloadKg().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal totalNetKg = ulds.stream()
                        .map(u -> u.getNetWeightKg() != null ? u.getNetWeightKg() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal utilization = totalNetKg
                        .multiply(BigDecimal.valueOf(100))
                        .divide(f.getMaxPayloadKg(), 2, RoundingMode.HALF_UP);
                row.put("utilizationPct", utilization);
            } else {
                row.put("utilizationPct", BigDecimal.ZERO);
            }

            return row;
        }).collect(Collectors.toList());
    }
}
