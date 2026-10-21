package id.enterpriseapps.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

import id.enterpriseapps.response.Response;
import id.enterpriseapps.service.NotificationService;
import id.enterpriseapps.service.logging.LoggingService;
import id.enterpriseapps.dto.logging.LoggingDto;

@Tag(name = "Notifications", description = "User notification management")
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private LoggingService loggingService;

    @Operation(summary = "Get notifications for a user")
    @GetMapping
    public ResponseEntity<Response> getNotifications(
            HttpServletRequest req,
            @RequestHeader Map<String, Object> h,
            @Parameter(description = "User ID") @RequestParam("userId") Long userId,
            @Parameter(description = "Page number") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "Items per page") @RequestParam(value = "limit", defaultValue = "20") int limit) {
        Response r = new Response();
        LoggingDto l = new LoggingDto("Get Notifications", new java.sql.Timestamp(System.currentTimeMillis()), req, h, null, new org.springframework.http.HttpHeaders(), null);
        try {
            loggingService.startController(l);
            r = notificationService.getNotifications(userId, page, limit);
            return ResponseEntity.ok(r);
        } catch (Exception e) {
            r = Response.builder().status("failed").errorMessage(e.getMessage()).build();
            return ResponseEntity.internalServerError().body(r);
        }
    }

    @Operation(summary = "Get unread notification count")
    @GetMapping("/unread-count")
    public ResponseEntity<Response> getUnreadCount(
            HttpServletRequest req,
            @RequestHeader Map<String, Object> h,
            @Parameter(description = "User ID") @RequestParam("userId") Long userId) {
        Response r = new Response();
        LoggingDto l = new LoggingDto("Get Unread Count", new java.sql.Timestamp(System.currentTimeMillis()), req, h, null, new org.springframework.http.HttpHeaders(), null);
        try {
            loggingService.startController(l);
            r = notificationService.getUnreadCount(userId);
            return ResponseEntity.ok(r);
        } catch (Exception e) {
            r = Response.builder().status("failed").errorMessage(e.getMessage()).build();
            return ResponseEntity.internalServerError().body(r);
        }
    }

    @Operation(summary = "Mark a notification as read")
    @PostMapping("/{alertId}/read")
    public ResponseEntity<Response> markAsRead(
            HttpServletRequest req,
            @RequestHeader Map<String, Object> h,
            @PathVariable("alertId") Long alertId,
            @Parameter(description = "User ID") @RequestParam("userId") Long userId) {
        Response r = new Response();
        LoggingDto l = new LoggingDto("Mark As Read", new java.sql.Timestamp(System.currentTimeMillis()), req, h, null, new org.springframework.http.HttpHeaders(), null);
        try {
            loggingService.startController(l);
            r = notificationService.markAsRead(alertId, userId);
            return ResponseEntity.ok(r);
        } catch (Exception e) {
            r = Response.builder().status("failed").errorMessage(e.getMessage()).build();
            return ResponseEntity.internalServerError().body(r);
        }
    }

    @Operation(summary = "Mark all notifications as read")
    @PostMapping("/read-all")
    public ResponseEntity<Response> markAllAsRead(
            HttpServletRequest req,
            @RequestHeader Map<String, Object> h,
            @Parameter(description = "User ID") @RequestParam("userId") Long userId) {
        Response r = new Response();
        LoggingDto l = new LoggingDto("Mark All As Read", new java.sql.Timestamp(System.currentTimeMillis()), req, h, null, new org.springframework.http.HttpHeaders(), null);
        try {
            loggingService.startController(l);
            r = notificationService.markAllAsRead(userId);
            return ResponseEntity.ok(r);
        } catch (Exception e) {
            r = Response.builder().status("failed").errorMessage(e.getMessage()).build();
            return ResponseEntity.internalServerError().body(r);
        }
    }
}

