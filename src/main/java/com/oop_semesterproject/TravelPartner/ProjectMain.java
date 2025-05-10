package com.oop_semesterproject.TravelPartner;
/**
 *
 * @author Burhan Arif
 */
import com.fasterxml.jackson.core.JsonProcessingException;
import io.javalin.Javalin;
import java.sql.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oop_semesterproject.TravelPartner.DTO.UserRequest;
import io.jsonwebtoken.*;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.mindrot.jbcrypt.BCrypt;

public class ProjectMain {

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7000);
        Database.initializeDatabase();
ObjectMapper objectMapper = new ObjectMapper();

app.post("/api/register", ctx -> {
    try {
        // Parse directly to UserRequest
        UserRequest userRequest = objectMapper.readValue(ctx.body(), UserRequest.class);
        
        // Validate required fields
        if (userRequest.name() == null || userRequest.email() == null || 
            userRequest.password() == null) {
            ctx.status(400).json(Map.of(
                "status", "error",
                "message", "Missing required fields"
            ));
            return;
        }
        
        Map<String, Object> result = Database.createUser(userRequest);
        ctx.status(201).json(result);
        
    } catch (JsonProcessingException e) {
        ctx.status(400).json(Map.of(
            "status", "error",
            "message", "Invalid JSON format",
            "details", e.getMessage()
        ));
    } catch (SQLException e) {
        ctx.status(500).json(Map.of(
            "status", "error",
            "message", "Database error",
            "details", e.getMessage()
        ));
    }
});
        app.post("/api/login", ctx -> {
            // Parse JSON body to a map
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:users.db")) {
                var body = objectMapper.readValue(ctx.body(), Map.class);
                String email = (String) body.get("email");
                String password = (String) body.get("password");
                // Processing request parameters
                Optional<UserRequest> FoundUserOpt = Database.getUserByEmail(email);
                UserRequest FoundUser = FoundUserOpt.get();
                boolean isPassValid = BCrypt.checkpw(password, FoundUser.password() );
                if (!isPassValid){
                    ctx.status(201).json(Map.of("message", "User not logged in"));
                }else{
                    password = FoundUser.password();
                String sql = "SELECT ID FROM users WHERE email = ? and password = ?";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setString(1, email);
                stmt.setString(2, password); 

                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    ctx.status(201).json(Map.of("message", "User logged in"));
                } else {
                    ctx.status(201).json(Map.of("message", "User not logged in"));
                }
                 }
            } catch (SQLException exp) {
                ctx.status(500).json(Map.of(
                        "error", "Database error",
                        "message", exp.getMessage())
                );
            }
        });

        app.post("/api/", ctx -> {
        
        });
        app.get("/users", ctx -> {
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:users.db")) {
                var rs = conn.createStatement().executeQuery("SELECT * FROM users");
                StringBuilder result = new StringBuilder();
                while (rs.next()) {
                    result.append(rs.getString("id")).append(": ").append(rs.getString("name")).append("\n");
                }
                ctx.result(result.toString());
            } catch (SQLException e) {
                ctx.status(500).result("DB error");
            }
        });

    }
}
