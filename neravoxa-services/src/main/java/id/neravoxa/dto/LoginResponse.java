package id.neravoxa.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
public class LoginResponse {
    private boolean success;
    private String message;
    private String token;
    private String refreshToken;
    private Long id;
    private Long userId;
    private String npp;
    private String npk;
    private String name;
    private String email;
    private String mobilePhone;
    private LocalDateTime lastLogin;
    private Long departmentId;
    private List<String> roles;
    private Map<String, List<String>> permissions;
}

