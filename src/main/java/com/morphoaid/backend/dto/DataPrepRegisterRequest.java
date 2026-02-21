package com.morphoaid.backend.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DataPrepRegisterRequest extends RegisterRequest {
    private String invitationToken;
}
