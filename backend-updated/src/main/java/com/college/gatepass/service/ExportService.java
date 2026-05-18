package com.college.gatepass.service;

import com.college.gatepass.entity.GatePass;
import com.college.gatepass.repository.GatePassRepository;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exports gate pass data as a downloadable CSV or PDF file.
 *
 * <p>Both methods load all passes from the database (or a filtered set), format
 * them, and return the result as a byte array. The controllers set the correct
 * {@code Content-Type} and {@code Content-Disposition} headers so the browser
 * saves the file automatically.
 *
 * <p>CSV uses OpenCSV. PDF uses OpenPDF (an LGPL fork of iText 2.1.7).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final GatePassRepository passes;

    /** Formats Instant values into a human-readable IST string for export files. */
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
                             .withZone(ZoneId.of("Asia/Kolkata"));

    // ── CSV ──────────────────────────────────────────────────────────────────

    /**
     * Exports all gate passes to a CSV file and returns the raw bytes.
     * The CSV includes a header row followed by one data row per pass.
     *
     * @return the full CSV file content as a UTF-8 byte array with BOM prefix
     *         so Excel opens it correctly without encoding issues
     */
    @Transactional(readOnly = true)
    public byte[] exportAllToCsv() {
        List<GatePass> all = passes.findAll();
        log.info("Exporting {} passes to CSV", all.size());
        return buildCsv(all);
    }

    /**
     * Builds the CSV bytes from a list of gate pass entities.
     *
     * @param list the gate passes to include in the CSV
     * @return the CSV content as a UTF-8 byte array
     */
    private byte[] buildCsv(List<GatePass> list) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Write UTF-8 BOM so Excel detects the encoding automatically
        baos.writeBytes(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

        try (CSVWriter csv = new CSVWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            // Header row
            csv.writeNext(new String[]{
                    "Pass ID", "Student ID", "Student Name", "Enrollment No",
                    "Pass Type", "Destination", "Reason",
                    "Leave At (IST)", "Return By (IST)", "Status",
                    "Warden ID", "Decision Note",
                    "Used At (IST)", "Returned At (IST)",
                    "Created At (IST)"
            });

            // Data rows
            for (GatePass p : list) {
                csv.writeNext(new String[]{
                        str(p.getId()),
                        p.getStudent() != null ? str(p.getStudent().getId()) : "",
                        p.getStudent() != null ? p.getStudent().getFullName() : "",
                        p.getStudent() != null ? nvl(p.getStudent().getEnrollmentNo()) : "",
                        nvl(p.getPassType()),
                        nvl(p.getDestination()),
                        nvl(p.getReason()),
                        p.getLeaveAt()   != null ? FMT.format(p.getLeaveAt())   : "",
                        p.getReturnBy()  != null ? FMT.format(p.getReturnBy())  : "",
                        nvl(p.getStatus()),
                        p.getWarden()    != null ? str(p.getWarden().getId())   : "",
                        nvl(p.getDecisionNote()),
                        p.getUsedAt()    != null ? FMT.format(p.getUsedAt())    : "",
                        p.getReturnedAt()!= null ? FMT.format(p.getReturnedAt()): "",
                        p.getCreatedAt() != null ? FMT.format(p.getCreatedAt()) : ""
                });
            }
        } catch (Exception e) {
            log.error("CSV export failed", e);
            throw new RuntimeException("CSV export failed: " + e.getMessage(), e);
        }

        return baos.toByteArray();
    }

    // ── PDF ──────────────────────────────────────────────────────────────────

    /**
     * Exports all gate passes to a nicely formatted PDF report and returns the bytes.
     *
     * @return the full PDF file content as a byte array
     */
    @Transactional(readOnly = true)
    public byte[] exportAllToPdf() {
        List<GatePass> all = passes.findAll();
        log.info("Exporting {} passes to PDF", all.size());
        return buildPdf(all);
    }

    /**
     * Builds the PDF bytes from a list of gate pass entities using OpenPDF.
     *
     * @param list the gate passes to include in the PDF
     * @return the PDF content as a byte array
     */
    private byte[] buildPdf(List<GatePass> list) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Page in landscape so all columns fit
        Document doc = new Document(PageSize.A4.rotate(), 24f, 24f, 36f, 36f);

        try {
            PdfWriter.getInstance(doc, baos);
            doc.open();

            // ── Title ─────────────────────────────────────────────────────────
            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD, Color.decode("#1a237e"));
            Paragraph title = new Paragraph("Digital Gate Pass Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);

            Font subFont = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.GRAY);
            Paragraph sub = new Paragraph(
                    "Generated: " + FMT.format(java.time.Instant.now())
                    + " IST  |  Total records: " + list.size(), subFont);
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingAfter(12f);
            doc.add(sub);

            // ── Table ─────────────────────────────────────────────────────────
            // 10 columns (widths are relative proportions)
            PdfPTable table = new PdfPTable(10);
            table.setWidthPercentage(100f);
            table.setWidths(new float[]{4, 8, 7, 6, 9, 7, 8, 7, 6, 8});

            // Header row
            String[] headers = {
                    "ID", "Student", "Enroll No", "Type",
                    "Destination", "Leave At", "Return By",
                    "Status", "Warden", "Created"
            };
            Font hFont = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
            Color headerBg = Color.decode("#1976d2");

            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, hFont));
                cell.setBackgroundColor(headerBg);
                cell.setPadding(5f);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            // Data rows — alternate row shading for readability
            Font dFont  = new Font(Font.HELVETICA, 7, Font.NORMAL, Color.BLACK);
            Color evenBg = Color.decode("#e3f2fd");
            Color oddBg  = Color.WHITE;

            for (int i = 0; i < list.size(); i++) {
                GatePass p   = list.get(i);
                Color rowBg  = (i % 2 == 0) ? evenBg : oddBg;

                String[] vals = {
                        str(p.getId()),
                        p.getStudent() != null ? p.getStudent().getFullName() : "-",
                        p.getStudent() != null ? nvl(p.getStudent().getEnrollmentNo()) : "-",
                        nvl(p.getPassType()),
                        nvl(p.getDestination()),
                        p.getLeaveAt()  != null ? FMT.format(p.getLeaveAt())  : "-",
                        p.getReturnBy() != null ? FMT.format(p.getReturnBy()) : "-",
                        nvl(p.getStatus()),
                        p.getWarden() != null ? p.getWarden().getFullName() : "-",
                        p.getCreatedAt()!= null ? FMT.format(p.getCreatedAt()): "-"
                };

                for (String v : vals) {
                    PdfPCell cell = new PdfPCell(new Phrase(v, dFont));
                    cell.setBackgroundColor(rowBg);
                    cell.setPadding(4f);
                    table.addCell(cell);
                }
            }

            doc.add(table);

        } catch (Exception e) {
            log.error("PDF export failed", e);
            throw new RuntimeException("PDF export failed: " + e.getMessage(), e);
        } finally {
            doc.close();
        }

        return baos.toByteArray();
    }

    // ── Private null-safe helpers ─────────────────────────────────────────────

    /**
     * Converts any object to its string representation safely (returns "" for null).
     *
     * @param o the object to convert
     * @return string value or empty string if null
     */
    private String str(Object o) {
        return o == null ? "" : o.toString();
    }

    /**
     * Returns the object's toString or empty string if null.
     *
     * @param o the object to convert
     * @return non-null string
     */
    private String nvl(Object o) {
        return o == null ? "" : o.toString();
    }
}