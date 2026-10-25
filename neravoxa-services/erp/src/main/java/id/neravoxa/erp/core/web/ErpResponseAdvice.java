package id.neravoxa.erp.core.web;

import com.fasterxml.jackson.databind.JsonNode;
import id.neravoxa.erp.core.ErpJson;
import id.neravoxa.erp.core.audit.ErpAuditService;
import id.neravoxa.erp.core.security.ErpActor;
import id.neravoxa.erp.core.security.ErpActorResolver;
import id.neravoxa.response.Response;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Membungkus body controller ERP ke {@link Response} platform dan mencatat jejak audit.
 *
 * <p>Controller ERP mengembalikan objek apa adanya (entity, Map, record, List) seperti
 * {@code res.json(body)} di Express. Advice ini:
 * <ol>
 *   <li>mengubah body lewat {@link ErpJson#tree(Object)} (relasi lazy yang tidak di-fetch dilewati),</li>
 *   <li>membungkusnya: {@code { status: "success", httpStatus, data: <body asli> }} — klien ERP
 *       membuka {@code data} sehingga bentuknya identik dengan respons ENTARIS,</li>
 *   <li>mencatat audit untuk request yang mengubah data.</li>
 * </ol>
 * Body yang sudah berupa {@link Response} (dari ErpExceptionHandler) hanya diaudit.
 * Download berkas (Resource/byte[]) tidak melewati converter JSON sehingga tidak tersentuh.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "id.neravoxa.erp")
@RequiredArgsConstructor
public class ErpResponseAdvice implements ResponseBodyAdvice<Object> {

    private final ErpAuditService audit;
    private final ErpActorResolver actors;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return AbstractJackson2HttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        int status = response instanceof ServletServerHttpResponse s ? s.getServletResponse().getStatus() : 200;
        HttpServletRequest servletRequest = request instanceof ServletServerHttpRequest r ? r.getServletRequest() : null;

        Response wrapped;
        JsonNode auditBody;
        if (body instanceof Response r) {
            wrapped = r;
            auditBody = r.getData() == null ? null : ErpJson.tree(r.getData());
        } else {
            JsonNode data = ErpJson.tree(body);
            HttpStatus hs = HttpStatus.resolve(status);
            wrapped = Response.builder()
                    .status("success")
                    .httpStatus(hs == null ? HttpStatus.OK : hs)
                    .data(data)
                    .build();
            auditBody = data;
        }

        if (servletRequest != null) {
            ErpActor actor = actors.currentOptional().orElse(null);
            audit.record(servletRequest, actor, auditBody);
        }
        return wrapped;
    }
}
