package id.enterpriseapps.config;

import java.io.IOException;
import java.io.InputStream;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

@Configuration
public class FirebaseConfig {

    @Value("${firebase.service-account-file:firebase-service-account.json}")
    private String serviceAccountFile;

    @PostConstruct
    public void initFirebase() {
        if (!FirebaseApp.getApps().isEmpty()) {
            return; // Already initialized
        }

        try {
            Resource resource = new ClassPathResource(serviceAccountFile);
            InputStream serviceAccount = resource.getInputStream();

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            FirebaseApp.initializeApp(options);
            System.out.println("[Firebase] Initialized successfully");
        } catch (IOException e) {
            System.err.println("[Firebase] WARNING: Could not initialize Firebase — " + e.getMessage());
            System.err.println("[Firebase] Push notifications will be disabled. "
                    + "Place " + serviceAccountFile + " in src/main/resources/ to enable.");
        }
    }
}
