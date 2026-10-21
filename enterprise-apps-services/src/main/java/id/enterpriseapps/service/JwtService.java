package id.enterpriseapps.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${app.jwt.secret:defaultSecretKeyPleaseChangeThisToSomethingMoreSecureAndLongEnough1234567890}")
    private String secretKey;

    @Value("${app.jwt.access-expiration}")
    private long accessExpiration;

    @Value("${app.jwt.refresh-expiration}")
    private long refreshExpiration;

    public long getRefreshExpiration() {
        return refreshExpiration;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateAccessToken(String username) {
        return generateToken(new HashMap<>(), username, accessExpiration);
    }

    public String generateAccessToken(Map<String, Object> extraClaims, String username) {
        return generateToken(extraClaims, username, accessExpiration);
    }

    public String generateRefreshToken(String username) {
        return generateToken(new HashMap<>(), username, refreshExpiration);
    }

    public String generateToken(Map<String, Object> extraClaims, String username, long expiration) {
        return buildToken(extraClaims, username, expiration);
    }

    private String buildToken(Map<String, Object> extraClaims, String username, long expiration) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey())
                .compact();
    }

    public boolean isTokenValid(String token, String username) {
        final String extractedUsername = extractUsername(token);
        return (extractedUsername.equals(username)) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(getSecretKeyBase64());
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // Helper to ensure key is long enough if using default simple string
    private String getSecretKeyBase64() {
        // If the key is not base64 encoded or too short, you might handle it here.
        // For simplicity assuming the property is a valid Base64 string or we encode it
        return java.util.Base64.getEncoder().encodeToString(secretKey.getBytes());
    }
}

