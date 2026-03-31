package com.morphoaid.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseNoteResponse {
    private Long id;
    private String note;
    private String authorName;
    private LocalDateTime createdAt;
}
