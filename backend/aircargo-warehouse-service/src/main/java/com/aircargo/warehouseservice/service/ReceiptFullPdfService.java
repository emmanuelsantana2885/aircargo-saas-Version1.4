package com.aircargo.warehouseservice.service;

import com.aircargo.warehouseservice.entity.ReceiptPiece;
import com.aircargo.warehouseservice.entity.WarehouseReceipt;
import com.aircargo.warehouseservice.repository.ReceiptPieceRepository;
import com.aircargo.warehouseservice.repository.WarehouseReceiptRepository;
import com.aircargo.common.error.ResourceNotFoundException;
import com.aircargo.common.util.TextUtil;
import com.aircargo.feign.client.MawbClient;
import com.aircargo.feign.dto.HawbDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReceiptFullPdfService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptFullPdfService.class);

    private final WarehouseReceiptRepository receiptRepository;
    private final ReceiptPieceRepository pieceRepository;
    private final PdfGenerationService pdfService;
    private final MawbClient mawbClient;

    public ReceiptFullPdfService(WarehouseReceiptRepository receiptRepository,
                                  ReceiptPieceRepository pieceRepository,
                                  PdfGenerationService pdfService,
                                  MawbClient mawbClient) {
        this.receiptRepository = receiptRepository;
        this.pieceRepository = pieceRepository;
        this.pdfService = pdfService;
        this.mawbClient = mawbClient;
    }

    @Transactional
    public byte[] generateReceiptPdf(UUID receiptId) {
        WarehouseReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Recibo no encontrado: " + receiptId));

        if (receipt.getPdfData() != null && receipt.getPdfData().length > 0) {
            log.info("Receipt {} served from persisted PDF ({}KB)", receiptId, receipt.getPdfData().length / 1024);
            return receipt.getPdfData();
        }

        List<ReceiptPiece> pieces = pieceRepository.findByReceiptId(receiptId);
        String html = buildReceiptHtml(receipt, pieces);
        byte[] pdf = pdfService.generatePdf(html);

        receipt.setPdfData(pdf);
        receiptRepository.save(receipt);

        log.info("Receipt {} PDF generated ({}KB, {} pieces)", receiptId, pdf.length / 1024, pieces.size());
        return pdf;
    }

    @Transactional(readOnly = true)
    public byte[] generateReceiptPdfFresh(UUID receiptId) {
        WarehouseReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException("Recibo no encontrado: " + receiptId));
        List<ReceiptPiece> pieces = pieceRepository.findByReceiptId(receiptId);
        String html = buildReceiptHtml(receipt, pieces);
        return pdfService.generatePdf(html);
    }

    private String buildReceiptHtml(WarehouseReceipt receipt, List<ReceiptPiece> pieces) {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        appendHead(sb, receipt);
        appendHeader(sb, receipt, dtf);
        appendFlags(sb, receipt);

        Map<String, List<ReceiptPiece>> byHawb = pieces.stream()
                .filter(p -> p.getHawbId() != null)
                .collect(Collectors.groupingBy(p -> p.getHawbId().toString(), LinkedHashMap::new, Collectors.toList()));

        Map<String, String> hawbNumbers = resolveHawbNumbers(receipt, byHawb);

        if (byHawb.isEmpty()) {
            sb.append("<h2>Piezas — Desglose</h2>");
            renderPieceTable(sb, pieces);
        } else {
            sb.append("<h2>Resumen General (MAWB)</h2>");
            renderSummaryTable(sb, pieces, byHawb, hawbNumbers);

            sb.append("<div class='page-break'></div>");
            sb.append("<h2>Anexo — Desglose por HAWB</h2>");
            int idx = 0;
            for (Map.Entry<String, List<ReceiptPiece>> entry : byHawb.entrySet()) {
                idx++;
                List<ReceiptPiece> hp = entry.getValue();
                String num = hawbNumbers.getOrDefault(entry.getKey(), "");
                sb.append("<div class='hawb-section'>");
                sb.append("<h3>HAWB ").append(esc(num)).append(" &#8212; ").append(idx).append(" de ").append(byHawb.size()).append("</h3>");
                renderPieceTable(sb, hp);
                renderHawbSummary(sb, hp, num);
                sb.append("</div>");
            }
        }

        appendRemarks(sb, receipt);
        appendSignatures(sb, receipt);
        appendFooter(sb);

        sb.append("</body></html>");
        return sb.toString();
    }

    private void appendHead(StringBuilder sb, WarehouseReceipt receipt) {
        sb.append("<!DOCTYPE html><html lang='es'><head><meta charset='UTF-8'/>");
        sb.append("<title>Recibo de Bodega - ").append(esc(receipt.getMawbNumber() != null ? receipt.getMawbNumber() : "")).append("</title>");
        sb.append("<style>");
        sb.append("@page{size:letter;margin:1.2cm 1.5cm}");
        sb.append("body{font-family:'JetBrains Mono',Helvetica,Arial,sans-serif;color:#111;font-size:9pt;margin:0;padding:0;line-height:1.4}");
        sb.append("h1{font-size:13pt;text-align:center;border-bottom:2px solid #111;padding-bottom:6px;margin:0 0 10px 0;text-transform:uppercase;letter-spacing:0.05em}");
        sb.append("h2{font-size:10pt;margin:12px 0 6px 0;padding:3px 6px;background:#1a1a1a;color:white;text-transform:uppercase;letter-spacing:0.04em}");
        sb.append("h3{font-size:9pt;margin:10px 0 4px 0;color:#333;border-bottom:1px solid #ccc;padding-bottom:2px}");
        sb.append(".info-grid{display:grid;grid-template-columns:1fr 1fr;gap:2px 20px;margin-bottom:10px;font-size:9pt}");
        sb.append(".info-grid .row{display:flex;gap:4px}");
        sb.append(".info-grid .label{font-weight:bold;min-width:90px;color:#444}");
        sb.append(".info-grid .value{color:#111}");
        sb.append(".flags{display:flex;gap:8px;margin-bottom:10px;font-size:8pt}");
        sb.append(".flags span{border:1px solid #999;padding:2px 6px;border-radius:2px}");
        sb.append(".flags .on{background:#111;color:white;border-color:#111}");
        sb.append("table{width:100%;border-collapse:collapse;margin-bottom:8px;font-size:8pt}");
        sb.append("th{background:#e5e7eb;border:1px solid #999;padding:3px 5px;text-align:center;font-weight:bold;font-size:7.5pt;text-transform:uppercase}");
        sb.append("td{border:1px solid #ccc;padding:2px 5px;text-align:center}");
        sb.append(".total-row{font-weight:bold;background:#f3f4f6;border-top:2px solid #111}");
        sb.append(".summary-row{background:#f9fafb}");
        sb.append(".hawb-section{margin:10px 0;padding:8px 10px;border:1px solid #ccc;border-radius:4px;background:#fafafa;page-break-inside:avoid}");
        sb.append(".hawb-summary{font-size:8pt;color:#555;margin-top:4px;padding:4px 6px;border:1px solid #e0e0e0;border-radius:3px;background:#f0f0f0}");
        sb.append(".sig-page{page-break-before:always}");
        sb.append(".sig-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-top:8px}");
        sb.append(".sig-card{border:1px solid #ccc;border-radius:4px;padding:8px 10px;background:#fafafa}");
        sb.append(".sig-card h4{font-size:9pt;margin:0 0 4px 0;color:#333;border-bottom:1px solid #ddd;padding-bottom:2px}");
        sb.append(".sig-row{display:flex;gap:8px;margin-top:4px}");
        sb.append(".sig-field{flex:1}");
        sb.append(".sig-field label{font-size:7pt;color:#666;text-transform:uppercase;display:block;margin-bottom:1px}");
        sb.append(".sig-field .val{font-size:8.5pt;color:#111;font-weight:bold}");
        sb.append(".sig-img{max-height:35px;max-width:180px;display:block;margin:4px 0;border:1px solid #eee;padding:2px;background:white}");
        sb.append(".footer{text-align:center;font-size:7pt;color:#999;margin-top:12px;padding-top:4px;border-top:1px solid #ddd}");
        sb.append(".remarks{font-size:8.5pt;color:#333;margin:6px 0;padding:6px 8px;border:1px solid #ddd;border-radius:3px;background:#fefefe}");
        sb.append(".page-break{page-break-before:always}");
        sb.append("</style></head><body>");
    }

    private void appendHeader(StringBuilder sb, WarehouseReceipt receipt, DateTimeFormatter dtf) {
        String mawbNum = esc(receipt.getMawbNumber() != null ? receipt.getMawbNumber() : "—");
        String dateStr = receipt.getReceiptDate() != null ? receipt.getReceiptDate().format(dtf) : "—";

        sb.append("<h1>Recibo de Bodega — Warehouse Receipt</h1>");
        sb.append("<div class='info-grid'>");
        sb.append(infoRow("MAWB", mawbNum));
        sb.append(infoRow("Fecha", dateStr));
        sb.append(infoRow("Shipper", esc(receipt.getShipperName())));
        sb.append(infoRow("Consignee", esc(receipt.getConsigneeName())));
        sb.append(infoRow("Origen", esc(receipt.getOrigin())));
        sb.append(infoRow("Destino", esc(receipt.getDestination())));
        sb.append(infoRow("Gateway", esc(receipt.getGatewayCfs())));
        sb.append(infoRow("Piezas AWB", String.valueOf(receipt.getAwbReportedPieces() != null ? receipt.getAwbReportedPieces() : 0)));
        sb.append(infoRow("Peso MAWB", formatDec(receipt.getMawbWeightGreatest()) + " LBS"));
        sb.append(infoRow("Peso Shipper", formatDec(receipt.getShipperReportedWeight()) + " KG"));
        sb.append("</div>");
    }

    private void appendFlags(StringBuilder sb, WarehouseReceipt receipt) {
        sb.append("<div class='flags'>");
        sb.append(flagSpan("Cash Only", receipt.getCashOnly()));
        sb.append(flagSpan("Booked ACOMS", receipt.getBookedInAcoms()));
        sb.append(flagSpan("Docs Provided", receipt.getDocsProvided()));
        sb.append(flagSpan("Customs", receipt.getCustomsCompleted()));
        sb.append(flagSpan("Pre-built", receipt.getPreBuilt()));
        sb.append("</div>");
    }

    private void appendRemarks(StringBuilder sb, WarehouseReceipt receipt) {
        String remarks = receipt.getRemarks();
        if (remarks != null && !remarks.isBlank()) {
            sb.append("<h2>Observaciones</h2>");
            sb.append("<div class='remarks'>").append(esc(remarks)).append("</div>");
        }
    }

    private void appendFooter(StringBuilder sb) {
        String footer = "AirCargo — Recibo de Bodega generado " + java.time.LocalDateTime.now().toString().replace("T", " ").substring(0, 16);
        sb.append("<div class='footer'>").append(esc(footer)).append("</div>");
    }

    private void renderSummaryTable(StringBuilder sb, List<ReceiptPiece> allPieces, Map<String, List<ReceiptPiece>> byHawb, Map<String, String> hawbNumbers) {
        int totalPcs = allPieces.stream().mapToInt(p -> p.getPieces() != null && p.getPieces() > 0 ? p.getPieces() : 1).sum();
        BigDecimal totalScaleLbs = allPieces.stream().map(p -> nz(p.getScaleWeightLbs())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDimLbs = allPieces.stream().map(p -> nz(p.getDimWeightLbs())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalScaleKg = allPieces.stream().map(p -> nz(p.getScaleWeightKg())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDimKg = allPieces.stream().map(p -> nz(p.getDimWeightKg())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalChgKg = allPieces.stream().map(p -> nz(p.getChargeableKg())).reduce(BigDecimal.ZERO, BigDecimal::add);

        sb.append("<table><thead><tr>");
        sb.append("<th>HAWB</th><th>Pzas</th><th>Scale LBS</th><th>Dim LBS</th>");
        sb.append("<th>Scale KGS</th><th>Dim KGS</th><th>CHG KGS</th>");
        sb.append("</tr></thead><tbody>");

        int idx = 0;
        for (Map.Entry<String, List<ReceiptPiece>> entry : byHawb.entrySet()) {
            idx++;
            List<ReceiptPiece> hp = entry.getValue();
            String num = hawbNumbers.getOrDefault(entry.getKey(), "");
            int pcs = hp.stream().mapToInt(p -> p.getPieces() != null && p.getPieces() > 0 ? p.getPieces() : 1).sum();
            BigDecimal scLbs = hp.stream().map(p -> nz(p.getScaleWeightLbs())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal dLbs = hp.stream().map(p -> nz(p.getDimWeightLbs())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal scKg = hp.stream().map(p -> nz(p.getScaleWeightKg())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal dKg = hp.stream().map(p -> nz(p.getDimWeightKg())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal chKg = hp.stream().map(p -> nz(p.getChargeableKg())).reduce(BigDecimal.ZERO, BigDecimal::add);

            sb.append("<tr class='summary-row'>");
            sb.append("<td>").append(esc(num)).append("</td>");
            sb.append("<td>").append(pcs).append("</td>");
            sb.append("<td>").append(scLbs.setScale(2, RoundingMode.HALF_UP)).append("</td>");
            sb.append("<td>").append(dLbs.setScale(2, RoundingMode.HALF_UP)).append("</td>");
            sb.append("<td>").append(scKg.setScale(2, RoundingMode.HALF_UP)).append("</td>");
            sb.append("<td>").append(dKg.setScale(2, RoundingMode.HALF_UP)).append("</td>");
            sb.append("<td>").append(chKg.setScale(2, RoundingMode.HALF_UP)).append("</td>");
            sb.append("</tr>");
        }

        sb.append("<tr class='total-row'>");
        sb.append("<td>TOTAL MAWB</td>");
        sb.append("<td>").append(totalPcs).append("</td>");
        sb.append("<td>").append(totalScaleLbs.setScale(2, RoundingMode.HALF_UP)).append("</td>");
        sb.append("<td>").append(totalDimLbs.setScale(2, RoundingMode.HALF_UP)).append("</td>");
        sb.append("<td>").append(totalScaleKg.setScale(2, RoundingMode.HALF_UP)).append("</td>");
        sb.append("<td>").append(totalDimKg.setScale(2, RoundingMode.HALF_UP)).append("</td>");
        sb.append("<td>").append(totalChgKg.setScale(2, RoundingMode.HALF_UP)).append("</td>");
        sb.append("</tr></tbody></table>");
    }

    private void renderHawbSummary(StringBuilder sb, List<ReceiptPiece> hawbPieces, String hawbNumber) {
        int pcs = hawbPieces.stream().mapToInt(p -> p.getPieces() != null && p.getPieces() > 0 ? p.getPieces() : 1).sum();
        BigDecimal scKg = hawbPieces.stream().map(p -> nz(p.getScaleWeightKg())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal dKg = hawbPieces.stream().map(p -> nz(p.getDimWeightKg())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal chKg = hawbPieces.stream().map(p -> nz(p.getChargeableKg())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal scLbs = hawbPieces.stream().map(p -> nz(p.getScaleWeightLbs())).reduce(BigDecimal.ZERO, BigDecimal::add);

        sb.append("<div class='hawb-summary'>");
        if (hawbNumber != null && !hawbNumber.isBlank()) {
            sb.append("HAWB: <strong>").append(esc(hawbNumber)).append("</strong> &#8212; ");
        }
        sb.append("Piezas: <strong>").append(pcs).append("</strong> &#8212; ");
        sb.append("Scale: <strong>").append(scLbs.setScale(2, RoundingMode.HALF_UP)).append(" LBS / ").append(scKg.setScale(2, RoundingMode.HALF_UP)).append(" KGS</strong> &#8212; ");
        sb.append("Facturable: <strong>").append(chKg.setScale(2, RoundingMode.HALF_UP)).append(" KGS</strong>");
        sb.append("</div>");
    }

    private Map<String, String> resolveHawbNumbers(WarehouseReceipt receipt, Map<String, List<ReceiptPiece>> byHawb) {
        Map<String, String> hawbNumbers = new HashMap<>();
        if (byHawb == null || byHawb.isEmpty()) return hawbNumbers;

        try {
            List<HawbDTO> hawbs = mawbClient.getHawbsByMawb(receipt.getMawbId());
            if (hawbs != null) {
                for (HawbDTO h : hawbs) {
                    if (h.getId() != null && h.getHawbNumber() != null) {
                        hawbNumbers.put(h.getId().toString(), h.getHawbNumber());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("No se pudo obtener HAWBs del MAWB {} para el PDF: {}", receipt.getMawbId(), e.getMessage());
        }

        for (String key : byHawb.keySet()) {
            if (hawbNumbers.containsKey(key)) continue;
            try {
                HawbDTO h = mawbClient.getHawbById(UUID.fromString(key));
                if (h != null && h.getHawbNumber() != null) {
                    hawbNumbers.put(key, h.getHawbNumber());
                }
            } catch (Exception e) {
                log.warn("No se pudo resolver HAWB {} para el PDF: {}", key, e.getMessage());
            }
        }

        return hawbNumbers;
    }

    private void renderPieceTable(StringBuilder sb, List<ReceiptPiece> pieces) {
        sb.append("<table><thead><tr>");
        sb.append("<th>#</th><th>Pcs</th><th>L (in)</th><th>W (in)</th><th>H (in)</th>");
        sb.append("<th>Dim Wt</th><th>Scale LBS</th><th>Dim LBS</th>");
        sb.append("<th>Scale KGS</th><th>Dim KGS</th><th>CHG KGS</th>");
        sb.append("</tr></thead><tbody>");

        int idx = 0;
        BigDecimal totalScaleLbs = BigDecimal.ZERO, totalDimLbs = BigDecimal.ZERO;
        BigDecimal totalScaleKg = BigDecimal.ZERO, totalDimKg = BigDecimal.ZERO, totalChgKg = BigDecimal.ZERO;
        int totalPcs = 0;

        for (ReceiptPiece p : pieces) {
            idx++;
            int qty = p.getPieces() != null && p.getPieces() > 0 ? p.getPieces() : 1;
            totalPcs += qty;
            totalScaleLbs = totalScaleLbs.add(nz(p.getScaleWeightLbs()));
            totalDimLbs = totalDimLbs.add(nz(p.getDimWeightLbs()));
            totalScaleKg = totalScaleKg.add(nz(p.getScaleWeightKg()));
            totalDimKg = totalDimKg.add(nz(p.getDimWeightKg()));
            totalChgKg = totalChgKg.add(nz(p.getChargeableKg()));

            sb.append("<tr>");
            sb.append("<td>").append(idx).append("</td>");
            sb.append("<td>").append(qty).append("</td>");
            sb.append("<td>").append(formatDec(p.getLengthIn())).append("</td>");
            sb.append("<td>").append(formatDec(p.getWidthIn())).append("</td>");
            sb.append("<td>").append(formatDec(p.getHeightIn())).append("</td>");
            sb.append("<td>").append(formatDec(p.getDimWeightKg())).append("</td>");
            sb.append("<td>").append(formatDec(p.getScaleWeightLbs())).append("</td>");
            sb.append("<td>").append(formatDec(p.getDimWeightLbs())).append("</td>");
            sb.append("<td>").append(formatDec(p.getScaleWeightKg())).append("</td>");
            sb.append("<td>").append(formatDec(p.getDimWeightKg())).append("</td>");
            sb.append("<td>").append(formatDec(p.getChargeableKg())).append("</td>");
            sb.append("</tr>");
        }

        sb.append("<tr class='total-row'>");
        sb.append("<td colspan='2'>TOTAL</td>");
        sb.append("<td colspan='3'>").append(totalPcs).append(" pzas</td>");
        sb.append("<td>—</td>");
        sb.append("<td>").append(totalScaleLbs.setScale(2, RoundingMode.HALF_UP)).append("</td>");
        sb.append("<td>").append(totalDimLbs.setScale(2, RoundingMode.HALF_UP)).append("</td>");
        sb.append("<td>").append(totalScaleKg.setScale(2, RoundingMode.HALF_UP)).append("</td>");
        sb.append("<td>").append(totalDimKg.setScale(2, RoundingMode.HALF_UP)).append("</td>");
        sb.append("<td>").append(totalChgKg.setScale(2, RoundingMode.HALF_UP)).append("</td>");
        sb.append("</tr></tbody></table>");
    }

    private void appendSignatures(StringBuilder sb, WarehouseReceipt receipt) {
        sb.append("<div class='sig-page'></div>");
        sb.append("<h2>Firmas — Evidencia</h2>");
        sb.append("<div class='sig-grid'>");

        // Dock
        sb.append("<div class='sig-card'>");
        sb.append("<h4>Recibido por (Almacen)</h4>");
        sb.append("<div class='sig-row'><div class='sig-field'><label>Nombre</label><div class='val'>");
        sb.append(esc(receipt.getPrintName() != null ? receipt.getPrintName() : (receipt.getReceivedByName() != null ? receipt.getReceivedByName() : "—")));
        sb.append("</div></div></div>");
        if (receipt.getDockSignature() != null && !receipt.getDockSignature().isEmpty()) {
            sb.append("<img class='sig-img' src='").append(receipt.getDockSignature()).append("' alt='Firma Dock' />");
        } else if (receipt.getReceivedBySigUrl() != null && !receipt.getReceivedBySigUrl().isEmpty()) {
            sb.append("<img class='sig-img' src='").append(receipt.getReceivedBySigUrl()).append("' alt='Firma Recibido' />");
        }
        sb.append("</div>");

        // Delivered By
        sb.append("<div class='sig-card'>");
        sb.append("<h4>Entregado por (Transportista)</h4>");
        sb.append("<div class='sig-row'>");
        sb.append("<div class='sig-field'><label>Nombre</label><div class='val'>").append(esc(receipt.getDeliveredByName())).append("</div></div>");
        sb.append("<div class='sig-field'><label>ID / Cedula</label><div class='val'>").append(esc(receipt.getDeliveredByIdNum())).append("</div></div>");
        sb.append("</div>");
        if (receipt.getDeliveredBySigUrl() != null && !receipt.getDeliveredBySigUrl().isEmpty()) {
            sb.append("<img class='sig-img' src='").append(receipt.getDeliveredBySigUrl()).append("' alt='Firma Entregado' />");
        }
        sb.append("</div>");

        // Broker
        sb.append("<div class='sig-card'>");
        sb.append("<h4>Representante de Broker / Agente de Carga</h4>");
        sb.append("<div class='sig-row'>");
        sb.append("<div class='sig-field'><label>Nombre</label><div class='val'>").append(esc(receipt.getBrokerName())).append("</div></div>");
        sb.append("<div class='sig-field'><label>ID / Cedula</label><div class='val'>").append(esc(receipt.getBrokerIdNum())).append("</div></div>");
        sb.append("</div>");
        if (receipt.getBrokerSigUrl() != null && !receipt.getBrokerSigUrl().isEmpty()) {
            sb.append("<img class='sig-img' src='").append(receipt.getBrokerSigUrl()).append("' alt='Firma Broker' />");
        }
        sb.append("</div>");

        sb.append("</div>");
    }

    private String infoRow(String label, String value) {
        return "<div class='row'><span class='label'>" + esc(label) + ":</span><span class='value'>" + (value != null ? value : "—") + "</span></div>";
    }

    private String flagSpan(String label, Boolean on) {
        return "<span class='" + (Boolean.TRUE.equals(on) ? "on" : "") + "'>" + esc(label) + "</span>";
    }

    private String formatDec(BigDecimal val) {
        if (val == null) return "0.00";
        return val.setScale(2, RoundingMode.HALF_UP).toString();
    }

    private BigDecimal nz(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }

    private String esc(String s) {
        return TextUtil.xmlEscape(s);
    }
}
