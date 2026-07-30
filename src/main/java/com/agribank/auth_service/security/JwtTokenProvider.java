package com.agribank.auth_service.security;

import com.agribank.auth_service.dto.response.UserInfo;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility provider for generating, parsing, and validating HS256-signed JWT tokens.
 */
@Component
public class JwtTokenProvider {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JwtTokenProvider.class);

    private final java.security.PrivateKey privateKey;
    private final java.security.PublicKey publicKey;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${app.security.jwt.expiration-ms}") long expirationMs) {
        this.expirationMs = expirationMs;
        try {
            this.privateKey = loadPrivateKey("certs/private_key.pem");
            this.publicKey = loadPublicKey("certs/public_key.pem");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA KeyPair for JWT signing/verification", e);
        }
    }

    private java.security.PrivateKey loadPrivateKey(String pemPath) throws Exception {
        byte[] keyBytes = loadPemKeyBytes(pemPath, "PRIVATE KEY");
        java.security.spec.PKCS8EncodedKeySpec spec = new java.security.spec.PKCS8EncodedKeySpec(keyBytes);
        java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    private java.security.PublicKey loadPublicKey(String pemPath) throws Exception {
        byte[] keyBytes = loadPemKeyBytes(pemPath, "PUBLIC KEY");
        java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(keyBytes);
        java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }

    private byte[] loadPemKeyBytes(String pemPath, String type) throws Exception {
        try (java.io.InputStream is = new org.springframework.core.io.ClassPathResource(pemPath).getInputStream()) {
            String pem = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String base64 = pem
                    .replace("-----BEGIN " + type + "-----", "")
                    .replace("-----END " + type + "-----", "")
                    .replaceAll("\\s+", "");
            return java.util.Base64.getDecoder().decode(base64);
        }
    }

    /**
     * Generate token with custom claims corresponding to Agribank requirements.
     */
    public String generateToken(UserInfo userInfo) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        Map<String, Object> claims = new HashMap<>();
        claims.put("fullName", userInfo.getFullName());
        claims.put("role", userInfo.getRole());
        claims.put("permissions", userInfo.getPermissions());
        claims.put("branchCode", userInfo.getBranchCode());
        claims.put("departmentCode", userInfo.getDepartmentCode());
        claims.put("email", userInfo.getEmail());

        io.jsonwebtoken.JwtBuilder builder = Jwts.builder()
                .subject(userInfo.getUsername())
                .issuer("sotay-auth-service")
                .claims(claims)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(privateKey, Jwts.SIG.RS256);

        builder.audience().single("product-service");

        return builder.compact();
    }

    /**
     * Validates whether the token signature is valid and token is not expired.
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (io.jsonwebtoken.security.SignatureException e) {
            log.error("JWT signature validation failed: {}", e.getMessage());
        } catch (io.jsonwebtoken.MalformedJwtException e) {
            log.error("Invalid JWT token format: {}", e.getMessage());
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.error("JWT token is expired: {}", e.getMessage());
        } catch (io.jsonwebtoken.UnsupportedJwtException e) {
            log.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Extracts username (subject) from JWT token.
     */
    public String getUsernameFromToken(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /**
     * Extracts all claims from JWT token.
     */
    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
