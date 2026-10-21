package id.enterpriseapps.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "status", "httpStatus", "errorCode", "errorMessage", "message", "page", "countData", "totalPage",
        "dataProtected", "data" })
public class Response {
    private String status;
    private HttpStatus httpStatus;
    private String errorCode;
    private String errorMessage;
    private Object data;
    private String dataProtected;
    private String message;
    private Integer page;
    private Long countData;
    private Integer totalPage;
}

