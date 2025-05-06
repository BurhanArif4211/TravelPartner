package com.oop_semesterproject.travelerotravela;

/**
 *
 * @author Burhan Arif
 */
import io.javalin.Javalin;
import java.sql.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TraveleroTravela {

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(7000);

        // Initialize sqlite Datab
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:users.db")) {
            String createTable
                    = "    CREATE TABLE IF NOT EXISTS users ("
                    + "    id TEXT PRIMARY KEY,"
                    + "    name TEXT NOT NULL,"
                    + "    email TEXT UNIQUE NOT NULL,"
                    + "    password TEXT NOT NULL,"
                    + "    phone_number TEXT"
                    + ");";
            conn.createStatement().execute(createTable);
        } catch (SQLException e) {
            e.printStackTrace();
        }

// Routes
        app.post("/api/register", ctx -> {
            ObjectMapper objectMapper = new ObjectMapper();

            // Parse JSON body to a map
            var body = objectMapper.readValue(ctx.body(), Map.class);

            String name = (String) body.get("name");
            String email = (String) body.get("email");
            String password = (String) body.get("password");
            String phoneNumber = (String) body.get("phone_number");
            String uuid = UUID.randomUUID().toString();

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:users.db")) {
                String sql = "INSERT INTO users (id, name, email, password, phone_number) VALUES (?, ?, ?, ?, ?)";
                PreparedStatement stmt = conn.prepareStatement(sql);

                stmt.setString(1, uuid);
                stmt.setString(2, name);
                stmt.setString(3, email);
                stmt.setString(4, password); // NOTE: Do not store plain passwords in real apps!
                stmt.setString(5, phoneNumber);
                stmt.executeUpdate();

                ctx.status(201).json(Map.of("id", uuid, "message", "User registered"));
            } catch (SQLException exp) {
                ctx.status(500).json(Map.of(
                        "error", "Database error",
                        "message", exp.getMessage())
                );
            }
        });
        app.post("/api/login", ctx -> {
            ObjectMapper objectMapper = new ObjectMapper();
            // Parse JSON body to a map
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:users.db")) {
                var body = objectMapper.readValue(ctx.body(), Map.class);
                String email = (String) body.get("email");
                String password = (String) body.get("password");
                String sql = "SELECT ID FROM users WHERE email = ? and password = ?";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setString(1, email);
                stmt.setString(2, password); // NOTE: Do not store plain passwords in real apps!

                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    ctx.status(201).json(Map.of("message", "User logged in"));
                } else {
                    ctx.status(201).json(Map.of("message", "User not logged in"));
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
