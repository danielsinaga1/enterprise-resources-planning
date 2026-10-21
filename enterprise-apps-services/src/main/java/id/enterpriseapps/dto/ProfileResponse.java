package id.enterpriseapps.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProfileResponse {
    private boolean success;
    private String message;
    
    // User fields
    private Long userId;
    private String npp;
    private String npk;
    private String name;
    private String email;
    private String mobilePhone;
    private LocalDateTime lastLogin;
}

