package id.neravoxa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    // User updateable fields
    private String name;
    private String mobilePhone;
    
    // Password change (optional)
    private String currentPassword;
    private String newPassword;
}

