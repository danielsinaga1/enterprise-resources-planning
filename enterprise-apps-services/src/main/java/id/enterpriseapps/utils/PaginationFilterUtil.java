package id.enterpriseapps.utils;

import id.enterpriseapps.service.logging.LoggingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class PaginationFilterUtil {
    private final LoggingService loggingService;

    // Date formatters
    private static final DateTimeFormatter FULL_ISO_DATE_TIME_FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final DateTimeFormatter FULL_DATE_TIME_FORMAT_WITH_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
    private static final DateTimeFormatter FULL_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter DATE_ONLY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Create pageable object with default sorting
    public Pageable createPageable(String page, String limit, String sortBy, String sortOrder) {
        sortBy = (sortBy != null && !sortBy.isEmpty()) ? sortBy : "id";
        // Dashboard clients send timestamp column names; Spring Data requires entity properties.
        sortBy = switch (sortBy) {
            case "created_at" -> "createdAt";
            case "updated_at" -> "updatedAt";
            default -> sortBy;
        };
        sortOrder = (sortOrder != null && !sortOrder.isEmpty()) ? sortOrder : "asc";
        Sort sort = Sort.by(Sort.Direction.fromString(sortOrder), sortBy);

        int pageInt = 0;  // Default to first page (0-based index)
        int limitInt = 10;  // Default to a limit of 10

        try {
            if (page != null && !page.trim().isEmpty()) {
                int parsedPage = Integer.parseInt(page.trim());
                pageInt = Math.max(0, parsedPage - 1);  // Ensure non-negative, 0-based index
            }
            if (limit != null && !limit.trim().isEmpty()) {
                int parsedLimit = Integer.parseInt(limit.trim());
                limitInt = Math.max(1, parsedLimit);  // Ensure minimum limit of 1
            }
        } catch (NumberFormatException e) {
            loggingService.error("PaginationFilterUtil createPageable - Invalid number format", e.getMessage(), Arrays.toString(e.getStackTrace()));
        }

        return PageRequest.of(pageInt, limitInt, sort);
    }
}

