package com.morphoaid.backend.service;

import com.morphoaid.backend.entity.CaseImage;
import com.morphoaid.backend.entity.User;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;

public interface StorageService {

    CaseImage uploadCaseImage(Long caseId, MultipartFile file, User uploader);

    String uploadFile(MultipartFile file, String folder);
    
    InputStream downloadFile(String objectKey);

    InputStream downloadImageContent(Long caseId, Long imageId);
}
