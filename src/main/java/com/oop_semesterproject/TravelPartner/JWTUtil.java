package com.oop_semesterproject.TravelPartner;

/**
 *
 * @author pc
 */
import io.javalin.http.Context;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;

//user defined
import com.oop_semesterproject.TravelPartner.exceptions.*;
public class JWTUtil {
    private static final Key key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    private static final long EXPIRATION_TIME_MS = 3600_000; // 1 hour

    public static String generateToken(String userId) {
        return Jwts.builder()
                .setSubject(userId)
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME_MS))
                .signWith(key)
                .compact();
    }

    public static String validateTokenAndGetUserId(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }
    public static String requireAuth(Context ctx) throws UnauthorizedException {
    String authHeader = ctx.header("Authorization");
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        throw new UnauthorizedException("Missing or invalid Authorization header");
    }

    String token = authHeader.substring(7); // 7 is the `Authorization Header in an HTTP POST request`
    try {
        return validateTokenAndGetUserId(token);
    } catch (Exception e) {
        throw new UnauthorizedException("Invalid or expired token");
    }
}

}

