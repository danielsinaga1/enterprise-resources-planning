package id.neravoxa.exception;

import id.neravoxa.constant.StatusConstant;
import id.neravoxa.response.Response;
import id.neravoxa.utils.ConvertUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationException implements AuthenticationEntryPoint {
    private final ConvertUtil convertUtil;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        Response body = Response.builder().status(StatusConstant.FAILED).httpStatus(HttpStatus.UNAUTHORIZED)
                .errorCode("Q1E00098").errorMessage("Access denied").build();
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json");
        response.getWriter().write(convertUtil.objectToJsonString(body));
    }
}

