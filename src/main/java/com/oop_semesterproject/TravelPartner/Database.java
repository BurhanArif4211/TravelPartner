package com.oop_semesterproject.TravelPartner;

/**
 *
 * @author Burhan Arif
 */
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
                    + "transportationType TEXT NOT NULL,"
                    + "available TEXT NOT NULL"
                    + ");";
            String createToRoutesTable
                    = "CREATE TABLE IF NOT EXISTS toRoutes("
                    + "id TEXT PRIMARY KEY,"
                    + "startPoint TEXT NOT NULL,"
                    + "endPoint TEXT NOT NULL,"
                    + "startTimestamp TEXT NOT NULL,"
                    + "startAddress TEXT NOT NULL,"
                    + "generalArea TEXT,"
                    + "FOREIGN KEY (id) REFERENCES users(id) ON DELETE CASCADE"
                    + ");";
            String createFromRoutesTable
                    = "CREATE TABLE IF NOT EXISTS fromRoutes ("
                    + "id TEXT PRIMARY KEY,"
                    + "startPoint TEXT NOT NULL,"
                    + "endPoint TEXT NOT NULL,"
                    + "startTimestamp TEXT NOT NULL,"
                    + "endAddress TEXT NOT NULL,"
                    + "generalArea TEXT,"
                    + "FOREIGN KEY (id) REFERENCES users(id) ON DELETE CASCADE"
                    + ");";
            // Add to your startup initialization
            String createConnectRequestsTable
                    = "CREATE TABLE IF NOT EXISTS connectRequests ("
                    + "userId TEXT PRIMARY KEY,"
                    + "requesters TEXT DEFAULT ''" // Stores comma-separated user IDs
                    + ");";

            String createConnectionsTable
                    = "CREATE TABLE IF NOT EXISTS connections ("
                    + "userId TEXT NOT NULL,"
                    + "connectedUserId TEXT NOT NULL,"
                    + "PRIMARY KEY (userId, connectedUserId)"
                    + ");";

            connectionDB.createStatement().execute(createConnectRequestsTable);
            connectionDB.createStatement().execute(createConnectionsTable);

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
            String sql = "INSERT INTO users (id, name, email, password, phone_number, transportationType, available) VALUES (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql);

            stmt.setString(1, uuid);
            stmt.setString(2, user.name());
            stmt.setString(3, user.email());
            stmt.setString(4, encPassword);
            stmt.setString(5, user.phone_number());
            stmt.setString(6, user.transportationType());
            stmt.setString(7, user.available());
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
    public static Map<String, Object> addRoute(AddRouteRequest route, String userID) throws SQLException, IllegalArgumentException {
        String sql;

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:root.db")) {

            if (route.type().equalsIgnoreCase("toRoutes")) {
                sql = """
                INSERT INTO toRoutes (id, startPoint, endPoint, startTimestamp, startAddress, generalArea)
                VALUES (?, ?, ?, ?, ?, ?)
            """;
            } else if (route.type().equalsIgnoreCase("fromRoutes")) {
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
        boolean isValidRoute = req.type().equalsIgnoreCase("fromRoutes") || req.type().equalsIgnoreCase("toRoutes");
        boolean isFromRoute = !req.type().equalsIgnoreCase("fromRoutes");

        if (!isValidRoute || req.limit() > 20) {
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

    public static UserDetailsResponse getUserDetails(String authenticatedUserId, String targetUserId)
            throws SQLException, IllegalArgumentException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:root.db")) {
            // Verify relationship exists
            boolean hasPendingRequest = hasPendingRequest(conn, authenticatedUserId, targetUserId);
            boolean alreadyConnected = isAlreadyConnected(conn, authenticatedUserId, targetUserId);
            boolean isSameUser = authenticatedUserId.equals(targetUserId);
            if (!hasPendingRequest && !alreadyConnected && !isSameUser) {
                throw new IllegalArgumentException("No relationship exists with this user");
            }

            // Rest of the method remains the same...
            Map<String, Object> user = getUser(conn, targetUserId);
            ToRoute toRoute = getToRoute(conn, targetUserId);
            FromRoute fromRoute = getFromRoute(conn, targetUserId);

            return new UserDetailsResponse(
                    (String) user.get("name"),
                    (String) user.get("phone_number"),
                    (String) user.get("transportationType"),
                    (String) user.get("available"),
                    toRoute,
                    fromRoute
            );
        }
    }

    private static boolean hasPendingRequest(Connection conn, String userId, String requesterId)
            throws SQLException {
        String sql = "SELECT requesters FROM connectRequests WHERE userId = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String requesters = rs.getString("requesters");
                return Arrays.asList(requesters.split(",")).contains(requesterId);
            }
        }
        return false;
    }

    private static boolean isAlreadyConnected(Connection conn, String user1, String user2)
            throws SQLException {
        String sql = "SELECT 1 FROM connections WHERE (userId = ? AND connectedUserId = ?) OR (userId = ? AND connectedUserId = ?) LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user1);
            stmt.setString(2, user2);
            stmt.setString(3, user2);
            stmt.setString(4, user1);
            return stmt.executeQuery().next();
        }
    }

    private static Map<String, Object> getUser(Connection conn, String userId)
            throws SQLException {
        String sql = "SELECT name, phone_number, transportationType, available FROM users WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                throw new SQLException("User not found");
            }

            return Map.of(
                    "name", rs.getString("name"),
                    "phone_number", rs.getString("phone_number"),
                    "transportationType", rs.getString("transportationType"),
                    "available", rs.getString("available")
            );
        }
    }

    private static ToRoute getToRoute(Connection conn, String userId) throws SQLException {
        String sql = "SELECT startPoint, endPoint, startTimestamp, startAddress, generalArea "
                + "FROM toRoutes WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? new ToRoute(
                    rs.getString("startPoint"),
                    rs.getString("endPoint"),
                    rs.getString("startTimestamp"),
                    rs.getString("startAddress"),
                    rs.getString("generalArea")
            ) : null;
        }
    }

    private static FromRoute getFromRoute(Connection conn, String userId) throws SQLException {
        String sql = "SELECT startPoint, endPoint, startTimestamp, endAddress, generalArea "
                + "FROM fromRoutes WHERE id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? new FromRoute(
                    rs.getString("startPoint"),
                    rs.getString("endPoint"),
                    rs.getString("startTimestamp"),
                    rs.getString("endAddress"),
                    rs.getString("generalArea")
            ) : null;
        }
    }

}
