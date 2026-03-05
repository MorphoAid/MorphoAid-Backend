package com.morphoaid.backend.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Lab export engine — packages ANALYZED/REVIEWED cases into a streaming ZIP.
 * ZIP contents:
 * images/<caseId>_<imageId>.<ext> — fetched from S3 via StorageService
 * labels.csv — anonymized classification metadata
 */
public interface LabExportService {

    /**
     * Writes export.zip directly to the HTTP response stream.
     * Uses ZipOutputStream to avoid buffering the entire zip in memory.
     *
     * @param response the current HTTP response (caller must NOT have written to it
     *                 yet)
     */
    void streamExport(HttpServletResponse response);
}
