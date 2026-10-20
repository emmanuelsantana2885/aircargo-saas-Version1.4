package com.aircargo.uldservice.service;

import com.aircargo.feign.client.MawbClient;
import com.aircargo.feign.client.BookingClient;
import com.aircargo.feign.dto.MawbDTO;
import com.aircargo.feign.dto.HawbDTO;
import com.aircargo.uldservice.dto.ScanLookupDTO;
import com.aircargo.uldservice.dto.ScanPieceRequest;
import com.aircargo.uldservice.dto.ScanPieceResult;
import com.aircargo.uldservice.entity.PieceSource;
import com.aircargo.uldservice.entity.Uld;
import com.aircargo.uldservice.entity.UldAwb;
import com.aircargo.uldservice.entity.UldPiece;
import com.aircargo.uldservice.repository.UldAwbRepository;
import com.aircargo.uldservice.repository.UldPieceRepository;
import com.aircargo.uldservice.repository.UldRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ScanService {

    private final UldRepository uldRepository;
    private final UldAwbRepository uldAwbRepository;
    private final UldPieceRepository uldPieceRepository;
    private final MawbClient mawbClient;
    private final BookingClient bookingClient;

    public ScanService(UldRepository uldRepository,
                       UldAwbRepository uldAwbRepository,
                       UldPieceRepository uldPieceRepository,
                       MawbClient mawbClient,
                       BookingClient bookingClient) {
        this.uldRepository = uldRepository;
        this.uldAwbRepository = uldAwbRepository;
        this.uldPieceRepository = uldPieceRepository;
        this.mawbClient = mawbClient;
        this.bookingClient = bookingClient;
    }

    @Transactional(readOnly = true)
    public ScanLookupDTO lookup(String code, UUID uldId) {
        String normalized = normalizeCode(code);

        // 1. Try as MAWB via Feign (raw code first, then normalized)
        MawbDTO mawb = findMawbByCode(normalized);
        if (mawb != null) {
            return buildMawbLookup(mawb, uldId);
        }

        // 1b. Try as HAWB via Feign (resolves to its parent MAWB)
        HawbDTO hawb = findHawbByCode(normalized);
        if (hawb != null && hawb.getMawbId() != null) {
            MawbDTO parent = mawbClient.getMawbById(hawb.getMawbId());
            if (parent != null) {
                return buildMawbLookup(parent, uldId);
            }
        }

        // 2. Try as ULD number
        Optional<Uld> uldOpt = uldRepository.findAll().stream()
                .filter(u -> u.getUldNumber() != null && normalizeCode(u.getUldNumber()).equals(normalized))
                .findFirst();
        if (uldOpt.isPresent()) {
            return buildUldLookup(uldOpt.get());
        }

        return null;
    }

    @Transactional
    @CacheEvict(value = {"uld-awbs", "ulds"}, allEntries = true)
    public ScanPieceResult registerPiece(ScanPieceRequest request, UUID scannedBy) {
        Uld uld = uldRepository.findById(request.getUldId())
                .orElseThrow(() -> new IllegalArgumentException("ULD no encontrada: " + request.getUldId()));

        String normalized = normalizeCode(request.getAwbNumber());

        // Resolve scanned code: first as MAWB, then as HAWB (resolves to its parent MAWB)
        MawbDTO mawb = findMawbByCode(normalized);
        String hawbNumber = request.getHawbNumber();

        if (mawb == null) {
            // Scanned code may be a HAWB number: resolve it to its parent MAWB
            HawbDTO hawb = findHawbByCode(normalized);
            if (hawb != null && hawb.getMawbId() != null) {
                mawb = mawbClient.getMawbById(hawb.getMawbId());
                if (mawb != null && (hawbNumber == null || hawbNumber.isBlank())) {
                    hawbNumber = hawb.getHawbNumber();
                }
            }
        }

        if (mawb == null) {
            return errorResult("Código no reconocido: " + request.getAwbNumber());
        }

        // Check piece limit
        long existingCount = uldPieceRepository.countByUldIdAndMawbId(uld.getId(), mawb.getId());
        int maxAllowed = getMaxPieces(mawb);

        if (existingCount >= maxAllowed && maxAllowed > 0) {
            return errorResult("Límite alcanzado: " + existingCount + "/" + maxAllowed +
                    " piezas para " + mawb.getAwbNumber());
        }

        int nextPieceNumber = (int) existingCount + 1;

        // Create UldPiece
        UldPiece piece = new UldPiece();
        piece.setUldId(uld.getId());
        piece.setMawbId(mawb.getId());
        piece.setAwbNumber(mawb.getAwbNumber());
        piece.setHawbNumber(hawbNumber);
        piece.setPieceNumber(nextPieceNumber);
        piece.setSource("BARCODE".equalsIgnoreCase(request.getSource()) ? PieceSource.BARCODE : PieceSource.MANUAL);
        piece.setScannedBy(scannedBy);
        piece.setScannedAt(OffsetDateTime.now());
        uldPieceRepository.save(piece);

        // Upsert UldAwb
        upsertUldAwb(uld.getId(), mawb, nextPieceNumber);

        ScanPieceResult result = new ScanPieceResult();
        result.setSuccess(true);
        result.setMessage("Pieza #" + nextPieceNumber + " registrada");
        result.setPieceNumber(nextPieceNumber);
        result.setAwbNumber(mawb.getAwbNumber());
        result.setMawbId(mawb.getId().toString());
        result.setTotalOnUld((int) existingCount + 1);
        result.setAvailablePieces(Math.max(0, maxAllowed - (int) existingCount - 1));
        return result;
    }

    @Transactional
    @CacheEvict(value = {"uld-awbs", "ulds"}, allEntries = true)
    public boolean undoLastPiece(UUID uldId, UUID mawbId) {
        Optional<UldPiece> lastOpt = uldPieceRepository.findFirstByUldIdAndMawbIdOrderByPieceNumberDesc(uldId, mawbId);
        if (lastOpt.isEmpty()) return false;

        UldPiece last = lastOpt.get();
        uldPieceRepository.delete(last);

        long remaining = uldPieceRepository.countByUldIdAndMawbId(uldId, mawbId);
        uldAwbRepository.findByUldIdAndMawbId(uldId, mawbId).ifPresent(uldAwb -> {
            uldAwb.setPieces((int) remaining);
            uldAwbRepository.save(uldAwb);
        });

        return true;
    }

    private String normalizeCode(String code) {
        if (code == null) return "";
        return code.replaceAll("[\\s\\-]", "").toUpperCase();
    }

    /**
     * Rebuild the canonical IATA form {@code XXX-XXXXXXXX} from an 11-digit code
     * (e.g. {@code 40605444320} -> {@code 406-05444320}). Returns the input unchanged
     * if it is not an 11-digit number.
     */
    private String toIataForm(String code) {
        if (code != null && code.matches("\\d{11}")) {
            return code.substring(0, 3) + "-" + code.substring(3);
        }
        return code;
    }

    /**
     * Lookup a MAWB by the scanned code. The code may be scanned with or without
     * IATA separators (e.g. {@code 406-05444320} vs {@code 40605444320}), while the
     * mawb-service stores the canonical dashed form and matches it exactly. We try the
     * normalized form first, then fall back to the canonical dashed form.
     */
    private MawbDTO findMawbByCode(String code) {
        try {
            MawbDTO mawb = mawbClient.getMawbByAwbNumber(code);
            if (mawb != null) return mawb;
        } catch (Exception ignored) {}
        String dashed = toIataForm(code);
        if (!dashed.equals(code)) {
            try {
                return mawbClient.getMawbByAwbNumber(dashed);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private HawbDTO findHawbByCode(String code) {
        try {
            HawbDTO hawb = mawbClient.getHawbByNumber(code);
            if (hawb != null) return hawb;
        } catch (Exception ignored) {}
        String dashed = toIataForm(code);
        if (!dashed.equals(code)) {
            try {
                return mawbClient.getHawbByNumber(dashed);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private int getMaxPieces(MawbDTO mawb) {
        int reserved = mawb.getPieces() != null ? mawb.getPieces() : 0;
        int booked = 0;
        try {
            var bookings = bookingClient.getBookingByMawbId(mawb.getId());
            if (bookings != null) {
                booked = 0; // We don't have skids from Feign, just use reserved
            }
        } catch (Exception ignored) {}
        return Math.max(reserved, booked);
    }

    private ScanLookupDTO buildMawbLookup(MawbDTO mawb, UUID uldId) {
        ScanLookupDTO dto = new ScanLookupDTO();
        dto.setType("MAWB");
        dto.setAwbNumber(mawb.getAwbNumber());
        dto.setMawbId(mawb.getId().toString());
        dto.setShipperName(mawb.getShipperName());
        dto.setConsigneeName(mawb.getConsigneeName());
        dto.setCommodityType(mawb.getCommodityType() != null ? mawb.getCommodityType() : "DRY_CARGO");
        dto.setDestination(mawb.getDestination());
        dto.setReservedPieces(mawb.getPieces() != null ? mawb.getPieces() : 0);

        int assigned = uldAwbRepository.findByMawbId(mawb.getId()).stream()
                .mapToInt(link -> link.getPieces() != null ? link.getPieces() : 0)
                .sum();
        dto.setAssignedTotal(assigned);

        int maxAllowed = getMaxPieces(mawb);
        dto.setAvailablePieces(Math.max(0, maxAllowed - assigned));

        if (uldId != null) {
            long existingOnUld = uldPieceRepository.countByUldIdAndMawbId(uldId, mawb.getId());
            dto.setExistingOnUld((int) existingOnUld);
        }

        return dto;
    }

    private ScanLookupDTO buildUldLookup(Uld uld) {
        ScanLookupDTO dto = new ScanLookupDTO();
        dto.setType("ULD");
        dto.setUldId(uld.getId().toString());
        dto.setUldNumber(uld.getUldNumber());
        dto.setUldType(uld.getUldType() != null ? uld.getUldType() : "UNK");
        dto.setFlightId(uld.getFlightId() != null ? uld.getFlightId().toString() : null);
        dto.setStatus(uld.getStatus() != null ? uld.getStatus().name() : "OPEN");

        int totalPieces = uldPieceRepository.findByUldId(uld.getId()).size();
        dto.setCurrentPieces(totalPieces);
        return dto;
    }

    private void upsertUldAwb(UUID uldId, MawbDTO mawb, int pieceCount) {
        Optional<UldAwb> existing = uldAwbRepository.findByUldIdAndMawbId(uldId, mawb.getId());
        if (existing.isPresent()) {
            UldAwb awb = existing.get();
            awb.setPieces(pieceCount);
            uldAwbRepository.save(awb);
        } else {
            UldAwb awb = new UldAwb();
            awb.setUldId(uldId);
            awb.setMawbId(mawb.getId());
            awb.setMawbLabel(mawb.getAwbNumber());
            awb.setDestination(mawb.getDestination());
            awb.setPieces(pieceCount);
            awb.setPiecesPct(100);
            uldAwbRepository.save(awb);
        }
    }

    private ScanPieceResult errorResult(String message) {
        ScanPieceResult result = new ScanPieceResult();
        result.setSuccess(false);
        result.setError(message);
        return result;
    }
}
