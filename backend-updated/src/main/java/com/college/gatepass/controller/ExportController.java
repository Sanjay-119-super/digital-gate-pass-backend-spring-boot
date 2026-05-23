package com.college.gatepass.controller;

import com.college.gatepass.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Admin-only endpoints that export all gate pass data as a downloadable CSV or PDF.
 *
 * <p>Both endpoints set the {@code Content-Disposition} header to
 * {@code attachment; filename="..."} so the browser saves the file automatically
 * rather than trying to display it inline.
 *
 * <p>Both operations are potentially slow for large datasets. Consider adding
 * pagination or date-range filtering if the pass table grows beyond 100,000 rows.
 */
@Tag(name = "Export", description = "Download gate pass data as CSV or PDF (admin only)")
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','WARDEN')")
public class ExportController {

    private final ExportService exportService;

    /**
     * Downloads all gate passes as a UTF-8 CSV file with a BOM prefix
     * so Excel opens it without encoding issues.
     *
     * @return the CSV file as an HTTP response with appropriate headers for download
     */
    @Operation(summary = "Export all gate passes to CSV")
    @GetMapping("/csv")
    public ResponseEntity<byte[]> exportCsv() {
        byte[] data = exportService.exportAllToCsv();
        String filename = "gate-passes-" + LocalDate.now() + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .contentLength(data.length)
                .body(data);
    }

    /**
     * Downloads all gate passes as a formatted PDF report in A4 landscape orientation.
     *
     * @return the PDF file as an HTTP response with appropriate headers for download
     */
    @Operation(summary = "Export all gate passes to PDF")
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> exportPdf() {
        byte[] data = exportService.exportAllToPdf();
        String filename = "gate-passes-" + LocalDate.now() + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(data.length)
                .body(data);
    }
}