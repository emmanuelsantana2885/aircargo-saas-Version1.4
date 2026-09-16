package com.aircargo.warehouseservice.service;

import com.aircargo.warehouseservice.entity.ReceiptPiece;
import com.aircargo.warehouseservice.entity.WarehouseReceipt;
import com.aircargo.warehouseservice.repository.ReceiptPieceRepository;
import com.aircargo.warehouseservice.repository.WarehouseReceiptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class ReceiptExportService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptExportService.class);

    private final WarehouseReceiptRepository receiptRepository;
    private final ReceiptPieceRepository pieceRepository;
    private final EvidenceSheetRenderer evidenceRenderer;
    private final MawbNumberResolver mawbNumberResolver;

    /** 1-indexed Excel row numbers matching the reference template layout */
    private static final int ROW_TITLE        = 1;
    private static final int ROW_GATEWAY      = 3;
    private static final int ROW_SHIPPER      = 4;
    private static final int ROW_MAWB_NUM     = 5;
    private static final int ROW_ORIGIN       = 6;
    private static final int ROW_DESTINATION  = 7;
    private static final int ROW_AWB_PIECES   = 8;
    private static final int ROW_MAWB_WEIGHT_LABEL = 9;
    private static final int ROW_MAWB_WEIGHT_VAL   = 10;
    private static final int ROW_PIECE_COUNT  = 15;
    private static final int ROW_DIM_FORMULA  = 17;
    private static final int ROW_TABLE_HEADER = 21;
    private static final int ROW_FIRST_DATA   = 22;
    private static final int ROW_LAST_DATA    = 46;
    private static final int ROW_TOTALS       = 47;
    private static final int ROW_BLANK_AFTER_TOTALS = 48;
    private static final int ROW_ACTUAL_WEIGHT = 49;
    private static final int ROW_SPACER_50    = 50;
    private static final int ROW_CHARGEABLE   = 51;
    private static final int ROW_SPACER_52    = 52;
    private static final int ROW_REMARKS      = 53;
    private static final int ROW_DOCK_SIG     = 56;
    private static final int ROW_PRINT_NAME   = 57;

    private static final int COL_A = 0, COL_B = 1, COL_C = 2, COL_D = 3, COL_E = 4;
    private static final int COL_F = 5, COL_G = 6, COL_H = 7, COL_I = 8, COL_J = 9;
    private static final int COL_K = 10, COL_L = 11;

    public ReceiptExportService(WarehouseReceiptRepository receiptRepository,
                                ReceiptPieceRepository pieceRepository,
                                ObjectMapper objectMapper,
                                MawbNumberResolver mawbNumberResolver) {
        this.receiptRepository = receiptRepository;
        this.pieceRepository = pieceRepository;
        this.evidenceRenderer = new EvidenceSheetRenderer(objectMapper);
        this.mawbNumberResolver = mawbNumberResolver;
    }

    @Transactional(readOnly = true)
    public ByteArrayInputStream exportReceipt(UUID receiptId) {
        long start = System.currentTimeMillis();
        log.debug("Exporting receipt {} to XLSX (template mode)", receiptId);

        WarehouseReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("Recibo no encontrado: " + receiptId));

        List<ReceiptPiece> pieces = pieceRepository.findByReceiptId(receiptId);
        byte[] data = buildFromTemplate(receipt, pieces);

        long elapsed = System.currentTimeMillis() - start;
        log.info("Receipt {} exported in {}ms ({} pieces, {}KB)",
                receiptId, elapsed, pieces.size(), data.length / 1024);

        return new ByteArrayInputStream(data);
    }

    @Transactional(readOnly = true)
    public byte[] generateBytes(UUID receiptId) {
        WarehouseReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("Recibo no encontrado: " + receiptId));
        List<ReceiptPiece> pieces = pieceRepository.findByReceiptId(receiptId);
        return buildFromTemplate(receipt, pieces);
    }

    @Transactional
    public byte[] generateAndPersistExcel(UUID receiptId) {
        WarehouseReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new IllegalArgumentException("Recibo no encontrado: " + receiptId));

        List<ReceiptPiece> pieces = pieceRepository.findByReceiptId(receiptId);
        byte[] data = buildFromTemplate(receipt, pieces);

        receipt.setExcelData(data);
        receiptRepository.save(receipt);

        log.info("Receipt {} Excel generated and persisted ({}KB, {} pieces)", receiptId, data.length / 1024, pieces.size());
        return data;
    }

    private byte[] buildFromTemplate(WarehouseReceipt receipt, List<ReceiptPiece> pieces) {
        try (InputStream templateStream = new ClassPathResource("templates/dock-receipt-template.xlsx").getInputStream();
             XSSFWorkbook wb = new XSSFWorkbook(templateStream);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.getSheet("DockReceipt");
            if (sheet == null) sheet = wb.getSheetAt(0);

            fillHeaderFields(sheet, receipt, pieces);
            fillPiecesData(sheet, receipt, pieces);
            fillRemarks(sheet, receipt);
            fillSignatures(sheet, receipt);

            // A17 template has IF formula returning string "366" — overwrite with the
            // per-receipt international divisor so F column formulas (=/A17) divide correctly
            int dimFactorIntl = receipt.getDimFactorIntl() != null && receipt.getDimFactorIntl() > 0
                    ? receipt.getDimFactorIntl() : 366;
            setNumericValue(sheet, ROW_DIM_FORMULA, COL_A, dimFactorIntl);

            // Recalculate all formulas so cached values reflect the new data
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            evaluator.evaluateAll();

            sheet.protectSheet("aircargo2024");

            if (hasSupportingDocs(receipt)) {
                ExcelExportStyles styles = new ExcelExportStyles(wb);
                evidenceRenderer.create(wb, receipt, styles);
            }

            wb.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Error building workbook from template for receipt {}", receipt.getId(), e);
            throw new RuntimeException("Error generando recibo de bodega: " + e.getMessage(), e);
        }
    }

    private void fillHeaderFields(Sheet sheet, WarehouseReceipt receipt, List<ReceiptPiece> pieces) {
        String awbNum = mawbNumberResolver.resolve(receipt.getMawbId(), receipt.getMawbNumber());

        setStringValue(sheet, ROW_GATEWAY, COL_D, safe(receipt.getGatewayCfs(), "SDQ"));
        setStringValue(sheet, ROW_SHIPPER, COL_D, safe(receipt.getShipperName(), ""));
        setStringValue(sheet, ROW_MAWB_NUM, COL_D, awbNum);
        setStringValue(sheet, ROW_ORIGIN, COL_D, safe(receipt.getOrigin(), ""));
        setStringValue(sheet, ROW_DESTINATION, COL_D, safe(receipt.getDestination(), ""));

        int totalPieceCount = pieces.stream()
                .mapToInt(p -> p.getPieces() != null && p.getPieces() > 0 ? p.getPieces() : 1)
                .sum();
        setNumericValue(sheet, ROW_AWB_PIECES, COL_D, totalPieceCount);

        // MAWB weight (D10) — referenced by G22 formula =+D10
        double mawbWeight = receipt.getMawbWeightGreatest() != null
                ? receipt.getMawbWeightGreatest().doubleValue() : 0.0;
        setNumericValue(sheet, ROW_MAWB_WEIGHT_VAL, COL_D, mawbWeight);
    }

    private void fillPiecesData(Sheet sheet, WarehouseReceipt receipt, List<ReceiptPiece> pieces) {
        for (int pi = 0; pi < 25; pi++) {
            int rowIdx = ROW_FIRST_DATA + pi; // 1-indexed Excel row

            if (pi < pieces.size()) {
                ReceiptPiece p = pieces.get(pi);
                int qty = p.getPieces() != null && p.getPieces() > 0 ? p.getPieces() : 1;
                double length = p.getLengthIn() != null ? p.getLengthIn().doubleValue() : 0.0;
                double width = p.getWidthIn() != null ? p.getWidthIn().doubleValue() : 0.0;
                double height = p.getHeightIn() != null ? p.getHeightIn().doubleValue() : 0.0;
                double scaleLbs = p.getScaleWeightLbs() != null ? p.getScaleWeightLbs().doubleValue() : 0.0;

                setNumericValue(sheet, rowIdx, COL_B, qty);
                setNumericValue(sheet, rowIdx, COL_C, length);
                setNumericValue(sheet, rowIdx, COL_D, width);
                setNumericValue(sheet, rowIdx, COL_E, height);
                setNumericValue(sheet, rowIdx, COL_G, scaleLbs);
            } else {
                // Clear template default data for unused rows (prevents ghost pieces)
                setNumericValue(sheet, rowIdx, COL_B, 0);
                setNumericValue(sheet, rowIdx, COL_C, 0);
                setNumericValue(sheet, rowIdx, COL_D, 0);
                setNumericValue(sheet, rowIdx, COL_E, 0);
                setNumericValue(sheet, rowIdx, COL_G, 0);
            }
        }
    }

    private void fillRemarks(Sheet sheet, WarehouseReceipt receipt) {
        String remarks = receipt.getRemarks() != null ? receipt.getRemarks() : "";
        setStringValue(sheet, ROW_REMARKS, COL_C, remarks);
    }

    private void fillSignatures(Sheet sheet, WarehouseReceipt receipt) {
        // Dock Signature (C56): printName or "—"
        String dockSig = (receipt.getPrintName() != null && !receipt.getPrintName().isEmpty())
                ? receipt.getPrintName()
                : (receipt.getDockSignature() != null ? "\u2713 Firmado" : "\u2014");
        setStringValue(sheet, ROW_DOCK_SIG, COL_C, dockSig);

        // Print Name (C57)
        setStringValue(sheet, ROW_PRINT_NAME, COL_C, safe(receipt.getPrintName(), "\u2014"));

        // Date/Time (G57)
        Cell dateCell = getOrCreateCell(sheet, ROW_PRINT_NAME, COL_G);
        if (receipt.getReceiptDate() != null) {
            LocalDateTime ldt = receipt.getReceiptDate().toLocalDateTime();
            Date date = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
            dateCell.setCellValue(date);
        } else {
            dateCell.setCellValue("\u2014");
        }
    }

    private void setStringValue(Sheet sheet, int row, int col, String value) {
        Cell cell = getOrCreateCell(sheet, row, col);
        cell.setCellValue(value != null ? value : "");
    }

    private void setNumericValue(Sheet sheet, int row, int col, double value) {
        Cell cell = getOrCreateCell(sheet, row, col);
        if (cell.getCellType() == CellType.FORMULA) {
            cell.setCellFormula(null);
        }
        cell.setCellValue(value);
    }

    private Cell getOrCreateCell(Sheet sheet, int row, int col) {
        // Excel rows are 1-indexed, Apache POI rows are 0-indexed
        Row r = sheet.getRow(row - 1);
        if (r == null) r = sheet.createRow(row - 1);
        Cell c = r.getCell(col);
        if (c == null) c = r.createCell(col);
        return c;
    }

    private static String safe(String s, String fallback) {
        return s != null && !s.isEmpty() ? s : fallback;
    }

    private boolean hasSupportingDocs(WarehouseReceipt receipt) {
        try {
            String raw = receipt.getSupportingDocs();
            return raw != null && !raw.isEmpty() && !"[]".equals(raw);
        } catch (Exception e) {
            return false;
        }
    }
}
