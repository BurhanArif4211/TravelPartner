package com.oop_semesterproject.TravelPartner;
/**
 *
 * @author Burhan Arif
 */
import com.oop_semesterproject.TravelPartner.DTO.UserRegisterRequest;
import com.oop_semesterproject.TravelPartner.exceptions.EmailAlreadyExistsException;
import java.sql.*;
import java.util.*;
import org.mindrot.jbcrypt.BCrypt;
//user defined
import com.oop_semesterproject.TravelPartner.DTO.*;
import com.oop_semesterproject.TravelPartner.exceptions.*;

public class Database {

    public static void initializeDatabase() {
        // Initialize sqlite Database
        try (Connection connectionDB = DriverManager.getConnection("jdbc:sqlite:root.db")) {
            String createUsersTable
                    = "CREATE TABLE IF NOT EXISTS users ("
                    + "id TEXT PRIMARY KEY,"
                    + "name TEXT NOT NULL,"
                    + "email TEXT UNIQUE NOT NULL,"
                    + "password TEXT NOT NULL,"
                    + "phone_number TEXT,"
                    + "transportationType TEXT NOT NULL," +
                      "available BOOLEAN NOT NULL"
                    + ");";
            String createToRoutesTable
                   ="CREATE TABLE IF NOT EXISTS toRoutes(" +
                    "id TEXT PRIMARY KEY," +
                    "startPoint TEXT NOT NULL," +
                    "endPoint TEXT NOT NULL," +
                    "startTimestamp TEXT NOT NULL," +
                    "startAddress TEXT NOT NULL," +
                    "generalArea TEXT,"+ 
                    "FOREIGN KEY (id) REFERENCES users(id) ON DELETE CASCADE"+ 
                    ");";
            String createFromRoutesTable 
                    ="CREATE TABLE IF NOT EXISTS fromRoutes (" +
                     "id TEXT PRIMARY KEY," +
                     "startPoint TEXT NOT NULL," +
                     "endPoint TEXT NOT NULL," +
                     "startTimestamp TEXT NOT NULL," +
                     "endAddress TEXT NOT NULL," +
                     "generalArea TEXT," + 
                     "FOREIGN KEY (id) REFERENCES users(id) ON DELETE CASCADE" +
                     ");";
            connectionDB.createStatement().execute(createUsersTable);
            connectionDB.createStatement().execute(createToRoutesTable);
            connectionDB.createStatement().execute(createFromRoutesTable);
            

        } catch (SQLException e) {
            e.printStackTrace();
        }

    }
    // Registration for new user
    public static Map<String, Object> createUser(UserRegisterRequest user) throws SQLException {
        String uuid = UUID.randomUUID().toString();
        String encPassword = BCrypt.hashpw(user.password(), BCrypt.gensalt());

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:root.db")) {
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
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE") || e.getMessage().contains("constraint failed")) {
                throw new EmailAlreadyExistsException("Email already exists.");
            } else {
                throw e;
            }
        }
    }
    // Login function
    public static LoginResponse loginUser(String email, String plainPassword)
            throws SQLException, UserNotFoundException, InvalidCredentialsException {

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:root.db")) {
            String sql = "SELECT * FROM users WHERE email = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, email);

            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                throw new UserNotFoundException("Invalid credentials");
            }

            String storedHashedPassword = rs.getString("password");

            if (!BCrypt.checkpw(plainPassword, storedHashedPassword)) {
                throw new InvalidCredentialsException("Invalid Credentials");
            }
            String token = JWTUtil.generateToken(rs.getString("id"));
            return new LoginResponse(
                    "success",
                    rs.getString("id"),
                    rs.getString("name"),
                    rs.getString("email"),
                    rs.getString("phone_number"),
                    token
            );
        }
    }
    //Main 
    public static Map<String, Object> addRoute(AddRouteRequest route,String userID) throws SQLException, IllegalArgumentException  {
    String sql;
    
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:root.db")) {
        
        if (route.type().equalsIgnoreCase("to")) {
            sql = """
                INSERT INTO toRoutes (id, startPoint, endPoint, startTimestamp, startAddress, generalArea)
                VALUES (?, ?, ?, ?, ?, ?)
            """;
        } else if (route.type().equalsIgnoreCase("from")) {
            sql = """
                INSERT INTO fromRoutes (id, startPoint, endPoint, startTimestamp, endAddress, generalArea)
                VALUES (?, ?, ?, ?, ?, ?)
            """;
        } else {
            throw new IllegalArgumentException("Route type must be 'to' or 'from'");
        }
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, userID);//from the arguments
        stmt.setString(2, route.startPoint());
        stmt.setString(3, route.endPoint());
        stmt.setString(4, route.startTimestamp());
        stmt.setString(5, route.address()); // startAddress or endAddress depending on type
        stmt.setString(6, route.generalArea());

        int rows = stmt.executeUpdate();
        if (rows == 0) {
            throw new SQLException("Route insertion failed");
        }

        return Map.of(
            "status", "success",
            "message", route.type() + "Route added",
            "userId", userID
        );
    }
}
    
    public static List<RouteResult> lookupRoutes(RouteLookupReponse req) throws SQLException {
    String table;
    boolean isValidRoute = req.type().equalsIgnoreCase("fromRoutes") || req.type().equalsIgnoreCase("toRoutes") ;
    boolean isFromRoute = !req.type().equalsIgnoreCase("fromRoutes");

    if (!isValidRoute || req.limit() > 20 ) {
        throw new IllegalArgumentException("Invalid Request'");
    }

    int offset = (req.page() - 1) * req.limit();
    StringBuilder sql = new StringBuilder("SELECT * FROM " + req.type());
    
    if (req.generalArea() != null && !req.generalArea().isBlank()) {
        sql.append(" WHERE generalArea = ?");
    }
    
    sql.append(" LIMIT ? OFFSET ?");

    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:root.db")) {
        PreparedStatement stmt = conn.prepareStatement(sql.toString());

        int paramIndex = 1;
        if (req.generalArea() != null && !req.generalArea().isBlank()) {
            stmt.setString(paramIndex++, req.generalArea());
        }

        stmt.setInt(paramIndex++, req.limit());
        stmt.setInt(paramIndex, offset);

        ResultSet rs = stmt.executeQuery();
        List<RouteResult> routes = new ArrayList<>();

        while (rs.next()) {
            routes.add(new RouteResult(
                "success",
                rs.getString("id"),
                rs.getString("startPoint"),
                rs.getString("endPoint"),
                rs.getString("startTimestamp"),
                isFromRoute ? rs.getString("startAddress") : rs.getString("endAddress"),
                rs.getString("generalArea")
            ));
        }

        return routes;
    }
}

    
    
    
// 
//    public static Optional<UserRequest> getUserByEmail(String email) {
//        String sql = "SELECT name, email, phone_number, password FROM users WHERE email = ?";
//
//        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:root.db"); PreparedStatement stmt = conn.prepareStatement(sql)) {
//
//            stmt.setString(1, email);
//
//            try (ResultSet rs = stmt.executeQuery()) {
//                if (rs.next()) {
//                    return Optional.of(new UserRequest(
//                            rs.getString("name"),
//                            rs.getString("email"),
//                            rs.getString("password"),
//                            rs.getString("phone_number")
//                    ));
//                }
//                return Optional.empty();
//            }
//        } catch (SQLException e) {
//            // Log the error properly in production
//            System.err.println("Error fetching user by email: " + e.getMessage());
//            return Optional.empty();
//        }
//    }
}
