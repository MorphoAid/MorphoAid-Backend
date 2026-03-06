package com.morphoaid.backend.dto;

import com.morphoaid.backend.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummary {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private Role role;
}
