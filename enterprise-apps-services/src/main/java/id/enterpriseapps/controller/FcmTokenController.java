package id.enterpriseapps.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import id.enterpriseapps.constant.StatusConstant;
import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.FcmService;

@Tag(name = "FCM", description = "Firebase Cloud Messaging token management")
@RestController
@RequestMapping("/api/v1/fcm")
public class FcmTokenController {

    @Autowired
    private FcmService fcmService;

    @Operation(summary = "Register FCM token for the current device")
    @PostMapping("/register")
    public ResponseEntity<Response> registerToken(@RequestBody Map<String, Object> body) {
        try {
            Long userId = Long.valueOf(body.get("userId").toString());
            String token = body.get("token").toString();
            String deviceInfo = body.containsKey("deviceInfo") ? body.get("deviceInfo").toString() : null;

            fcmService.registerToken(userId, token, deviceInfo);

            return ResponseEntity.ok(Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .message("FCM token registered")
                    .build());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Response.builder()
                    .status(StatusConstant.FAILED)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .errorMessage("Failed to register FCM token: " + e.getMessage())
                    .build());
        }
    }

    @Operation(summary = "Unregister FCM token (logout)")
    @PostMapping("/unregister")
    public ResponseEntity<Response> unregisterToken(@RequestBody Map<String, Object> body) {
        try {
            String token = body.get("token").toString();
            fcmService.removeToken(token);

            return ResponseEntity.ok(Response.builder()
                    .status(StatusConstant.SUCCESS)
                    .httpStatus(HttpStatus.OK)
                    .message("FCM token removed")
                    .build());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Response.builder()
                    .status(StatusConstant.FAILED)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .errorMessage("Failed to unregister FCM token: " + e.getMessage())
                    .build());
        }
    }
}

