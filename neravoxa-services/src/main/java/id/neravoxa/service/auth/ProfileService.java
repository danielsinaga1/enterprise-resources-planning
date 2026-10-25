package id.neravoxa.service.auth;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import id.neravoxa.auth.entity.AuthUser;
import id.neravoxa.auth.repository.AuthUserRepository;
import id.neravoxa.dto.ProfileResponse;
import id.neravoxa.dto.UpdateProfileRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;

    public ProfileResponse getProfile(Long userId) {
        try {
            log.info("Getting profile for user ID: {}", userId);

            Optional<AuthUser> userOpt = authUserRepository.findById(userId);
            if (userOpt.isEmpty()) {
                return ProfileResponse.builder()
                        .success(false)
                        .message("User tidak ditemukan")
                        .build();
            }

            AuthUser user = userOpt.get();

            return ProfileResponse.builder()
                    .success(true)
                    .message("Profil berhasil diambil")
                    .userId(user.getId())
                    .npp(user.getNpp())
                    .npk(user.getNpk())
                    .name(user.getName())
                    .email(user.getEmail())
                    .mobilePhone(user.getMobilePhone())
                    .lastLogin(user.getLastLogin())
                    .build();
        } catch (Exception e) {
            log.error("Error getting profile: {}", e.getMessage(), e);
            return ProfileResponse.builder()
                    .success(false)
                    .message("Terjadi kesalahan: " + e.getMessage())
                    .build();
        }
    }

    @Transactional(transactionManager = "authTransactionManager")
    public ProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        try {
            log.info("Updating profile for user ID: {}", userId);

            Optional<AuthUser> userOpt = authUserRepository.findById(userId);
            if (userOpt.isEmpty()) {
                return ProfileResponse.builder()
                        .success(false)
                        .message("User tidak ditemukan")
                        .build();
            }

            AuthUser user = userOpt.get();

            if (request.getName() != null && !request.getName().isBlank()) {
                user.setName(request.getName());
            }
            if (request.getMobilePhone() != null && !request.getMobilePhone().isBlank()) {
                user.setMobilePhone(request.getMobilePhone());
            }

            if (request.getNewPassword() != null && !request.getNewPassword().isBlank()) {
                if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
                    return ProfileResponse.builder()
                            .success(false)
                            .message("Password lama harus diisi untuk mengubah password")
                            .build();
                }

                if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                    return ProfileResponse.builder()
                            .success(false)
                            .message("Password lama tidak sesuai")
                            .build();
                }

                user.setPassword(passwordEncoder.encode(request.getNewPassword()));
                log.info("Password updated for user: {}", user.getEmail());
            }

            user.setUpdatedBy(user.getEmail());
            authUserRepository.save(user);

            log.info("Profile updated successfully for user: {}", user.getEmail());

            return getProfile(userId);
        } catch (Exception e) {
            log.error("Error updating profile: {}", e.getMessage(), e);
            return ProfileResponse.builder()
                    .success(false)
                    .message("Terjadi kesalahan: " + e.getMessage())
                    .build();
        }
    }
}
