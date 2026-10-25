package id.neravoxa.property;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.config")
public class AppConfigProperties {
    private String allowedOrigins;
}

