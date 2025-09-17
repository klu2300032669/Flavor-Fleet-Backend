package com.flavorfleet.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);
    private final String SECRET_KEY;
    private final SecretKey key;

    public JwtUtil() {
        // Retrieve secret from environment (no hardcoded fallback for security)
        this.SECRET_KEY = System.getenv("JWT_SECRET");
        if (this.SECRET_KEY == null) {
            throw new IllegalStateException("JWT_SECRET environment variable is not set");
        }
        // Decode Base64 secret key
        byte[] decodedKey = Base64.getDecoder().decode(SECRET_KEY);
        this.key = Keys.hmacShaKeyFor(decodedKey);
        logger.info("JWT secret key initialized");
        
    }

    public String getEmailFromToken(String token) {
        try {
            return getClaimFromToken(token, Claims::getSubject);
        } catch (Exception e) {
            logger.error("Failed to extract email from token: {}", e.getMessage());
            return null;
        }
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    private Claims getAllClaimsFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        } catch (Exception e) {
            logger.error("Error parsing claims from token: {}", e.getMessage());
            throw e;
        }
    }

    // Add this method to your existing JwtUtil class
    public Boolean validateToken(String token) {
        try {
            final String email = getEmailFromToken(token);
            return (email != null && !isTokenExpired(token));
        } catch (Exception e) {
            logger.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public String generateToken(String email) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, email);
    }

    private String createToken(Map<String, Object> claims, String subject) {
        try {
            return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 86400)) // 24 hours
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
        } catch (Exception e) {
            logger.error("Failed to create token for subject {}: {}", subject, e.getMessage());
            throw e;
        }
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        try {
            final String email = getEmailFromToken(token);
            return (email != null && email.equals(userDetails.getUsername()) && !isTokenExpired(token));
        } catch (Exception e) {
            logger.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    private Boolean isTokenExpired(String token) {
        try {
            final Date expiration = getClaimFromToken(token, Claims::getExpiration);
            return expiration.before(new Date());
        } catch (Exception e) {
            logger.error("Error checking token expiration: {}", e.getMessage());
            return true;
        }
    }
}