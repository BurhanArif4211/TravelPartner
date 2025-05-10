package com.oop_semesterproject.TravelPartner;
import com.oop_semesterproject.TravelPartner.DTO.UserRequest;
import java.sql.*;
import java.util.*;
import io.jsonwebtoken.*;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Date;
import org.mindrot.jbcrypt.BCrypt;

public class Database{
public static void initializeDatabase() {
  // Initialize sqlite Database
try (Connection connectionDB = DriverManager.getConnection("jdbc:sqlite:users.db")) {
    String createTable
                    = "    CREATE TABLE IF NOT EXISTS users ("
                    + "    id TEXT PRIMARY KEY,"
                    + "    name TEXT NOT NULL,"
                    + "    email TEXT UNIQUE NOT NULL,"
                    + "    password TEXT NOT NULL,"
                    + "    phone_number TEXT"
                    + ");";
    connectionDB.createStatement().execute(createTable);

} 
catch (SQLException e) {
        e.printStackTrace();
    }

}
  public static Map<String, Object> createUser(UserRequest user) throws SQLException {
        String uuid = UUID.randomUUID().toString();
        String encPassword = BCrypt.hashpw(user.password(), BCrypt.gensalt());

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:users.db")) {
            String sql = "INSERT INTO users (id, name, email, password, phone_number) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql);

            stmt.setString(1, uuid);
            stmt.setString(2, user.name());
            stmt.setString(3, user.email());
            stmt.setString(4, encPassword);
            stmt.setString(5, user.phoneNumber());
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("Creating user failed, no rows affected.");
            }
            
            return Map.of(
                "status", "success",
                "id", uuid,
                "message", "User registered successfully"
            );
        }
    }
 public static Optional<UserRequest> getUserByEmail(String email) {
    String sql = "SELECT name, email, phone_number, password FROM users WHERE email = ?";
    
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:users.db");
         PreparedStatement stmt = conn.prepareStatement(sql)) {
         
        stmt.setString(1, email);
        
        try (ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return Optional.of(new UserRequest(
                    rs.getString("name"),
                    rs.getString("email"),
                    rs.getString("password"),  // Note: You might want to exclude password
                    rs.getString("phone_number")
                ));
            }
            return Optional.empty();
        }
    } catch (SQLException e) {
        // Log the error properly in production
        System.err.println("Error fetching user by email: " + e.getMessage());
        return Optional.empty();
    }
}
}


