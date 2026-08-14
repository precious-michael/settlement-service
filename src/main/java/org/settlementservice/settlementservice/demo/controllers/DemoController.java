package org.settlementservice.settlementservice.demo.controllers;

import lombok.RequiredArgsConstructor;
import org.settlementservice.settlementservice.demo.dtos.DemoFileGenerateResponse;
import org.settlementservice.settlementservice.demo.services.DemoFileGeneratorService;
import org.settlementservice.settlementservice.dtos.response.SettlementServiceResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final DemoFileGeneratorService demoFileGeneratorService;

    @Value("${demo.files.directory:./demo-files}")
    private String demoFilesDirectory;

    /**
     * Generate demo CSV files for testing the upload flow.
     * Creates bank statement CSV, settlement report CSV, and internal records.
     *
     * @param accountId Account to generate data for
     * @param count Number of transactions (default: 10)
     * @param mismatchRate Percentage with amount mismatches 0.0-1.0 (default: 0.2 = 20%)
     * @return File paths and download URLs
     */
    @PostMapping("/generate-files")
    public ResponseEntity<SettlementServiceResponse<DemoFileGenerateResponse>> generateFiles(
            @RequestParam Long accountId,
            @RequestParam(defaultValue = "10") int count,
            @RequestParam(defaultValue = "0.2") double mismatchRate) {

        DemoFileGenerateResponse response = demoFileGeneratorService.generateFiles(accountId, count, mismatchRate);
        return ResponseEntity.ok(SettlementServiceResponse.success(
                "Demo files generated successfully. Follow the instructions to test the upload flow.",
                response));
    }

    /**
     * Download a generated demo file.
     *
     * @param fileName File name from generate-files response
     * @return File download
     */
    @GetMapping("/download/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) {
        try {
            Path filePath = Paths.get(demoFilesDirectory).resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new RuntimeException("File not found: " + fileName);
            }

            String contentType = fileName.endsWith(".xlsx")
                    ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    : "text/csv";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (IOException e) {
            throw new RuntimeException("Failed to download file: " + fileName, e);
        }
    }
}
