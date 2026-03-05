package com.morphoaid.backend.service;

import com.morphoaid.backend.dto.CaseNoteResponse;
import com.morphoaid.backend.dto.ClinicalCaseResponse;
import com.morphoaid.backend.entity.User;
import org.springframework.web.multipart.MultipartFile;
import java.io.OutputStream;
import java.util.List;

public interface ClinicalCaseService {
    ClinicalCaseResponse uploadCase(MultipartFile image, String provinceCode, String provinceName,
            Boolean consent, String patientMetadata, User uploader);

    ClinicalCaseResponse getCaseById(Long id, User currentUser);

    CaseNoteResponse addNote(Long caseId, String note, User author);

    List<CaseNoteResponse> getNotes(Long caseId, User currentUser);

    void exportPdf(Long caseId, OutputStream outputStream, User currentUser);
}
