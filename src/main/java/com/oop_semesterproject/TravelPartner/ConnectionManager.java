package com.oop_semesterproject.TravelPartner;

import com.oop_semesterproject.TravelPartner.Database;
/**
 *
 * @author Burhan Arif pc
 */
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
//user defined

public class ConnectionManager {

    private static final int MAX_REQUESTS = 10;
    private static final String SEPARATOR = ",";

    public static Map<String, Object> sendRequest(String senderId, String receiverId) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:root.db")) {
            conn.setAutoCommit(false);

            // NEW: Check if sender is already connected
            if (isUserConnected(conn, senderId)) {
                return Map.of(
                        "status", "error",
                        "message", "You are already connected to someone"
                );
            }

            // NEW: Check if receiver is already connected
            if (isUserConnected(conn, receiverId)) {
                return Map.of(
                        "status", "error",
                        "message", "Target user is already connected"
                );
            }
            // Check if already connected
            if (connectionExists(conn, senderId, receiverId)) {
                return Map.of(
                        "status", "error",
                        "message", "Users are already connected"
                );
            }

            // Get existing requests
            String requesters = getRequesters(conn, receiverId);
            List<String> requesterList = new ArrayList<>(Arrays.asList(requesters.split(SEPARATOR)));

            // Remove empty strings from list
            requesterList.removeIf(String::isEmpty);

            // Check for duplicate request
            if (requesterList.contains(senderId)) {
                return Map.of(
                        "status", "error",
                        "message", "Request already sent"
                );
            }

            // Check request limit
            if (requesterList.size() >= MAX_REQUESTS) {
                return Map.of(
                        "status", "error",
                        "message", "User's request box is full"
                );
            }

            // Add new request
            requesterList.add(senderId);
            String newRequesters = String.join(SEPARATOR, requesterList);
            updateRequesters(conn, receiverId, newRequesters);

            conn.commit();
            return Map.of(
                    "status", "success",
                    "message", "Connection request sent"
            );
        }
    }

    public static Map<String, Object> acceptRequest(String receiverId, String requesterId) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:root.db")) {
            conn.setAutoCommit(false);
            // Check if either user is already connected
            if (isUserConnected(conn, receiverId) || isUserConnected(conn, requesterId)) {
                return Map.of(
                        "status", "error",
                        "message", "One or both users are already connected"
                );
            }
            // Check if already connected
            if (connectionExists(conn, receiverId, requesterId)) {
                return Map.of(
                        "status", "error",
                        "message", "Users are already connected"
                );
            }
            // Get requesters and remove the accepted one
            String requesters = getRequesters(conn, receiverId);
            List<String> requesterList = new ArrayList<>(Arrays.asList(requesters.split(SEPARATOR)));
            requesterList.removeIf(String::isEmpty);

            if (!requesterList.contains(requesterId)) {
                return Map.of(
                        "status", "error",
                        "message", "No such request exists"
                );
            }

            requesterList.remove(requesterId);
            String newRequesters = String.join(SEPARATOR, requesterList);
            updateRequesters(conn, receiverId, newRequesters);

            // Create bidirectional connection
            createConnection(conn, receiverId, requesterId);
            createConnection(conn, requesterId, receiverId);

            // NEW: Delete all pending requests for both users
            deleteUserRequests(conn, receiverId);
            deleteUserRequests(conn, requesterId);

            conn.commit();
            return Map.of(
                    "status", "success",
                    "message", "Connection established"
            );
        }
    }

// NEW: Delete all requests involving a user
    private static void deleteUserRequests(Connection conn, String userId) throws SQLException {
        // Delete requests where user is the receiver
        String deleteReceiverSql = "DELETE FROM connectRequests WHERE userId = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteReceiverSql)) {
            stmt.setString(1, userId);
            stmt.executeUpdate();
        }

        // Remove user from all requesters lists
        String selectSql = "SELECT userId, requesters FROM connectRequests";
        Map<String, String> updates = new HashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String receiverId = rs.getString("userId");
                String requesters = rs.getString("requesters");
                List<String> ids = new ArrayList<>(Arrays.asList(requesters.split(",")));
                if (ids.remove(userId)) {
                    updates.put(receiverId, String.join(",", ids));
                }
            }
        }

        // Apply updates
        String updateSql = "UPDATE connectRequests SET requesters = ? WHERE userId = ?";
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                stmt.setString(1, entry.getValue());
                stmt.setString(2, entry.getKey());
                stmt.executeUpdate();
            }
        }
    }

// NEW: Check if user has any connection
    private static boolean isUserConnected(Connection conn, String userId) throws SQLException {
        String sql = "SELECT 1 FROM connections WHERE userId = ? LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            return stmt.executeQuery().next();
        }
    }

    public static Map<String, Object> getRequests(String userId) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:root.db")) {
            String requesters = getRequesters(conn, userId);
            List<String> requests = Arrays.stream(requesters.split(SEPARATOR))
                    .filter(id -> !id.isEmpty())
                    .collect(Collectors.toList());

            return Map.of(
                    "status", "success",
                    "requests", requests
            );
        }
    }

    public static Map<String, Object> getConnections(String userId) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:root.db")) {
            String sql = "SELECT connectedUserId FROM connections WHERE userId = ?";
            List<String> connections = new ArrayList<>();

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, userId);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    connections.add(rs.getString("connectedUserId"));
                }
            }

            return Map.of(
                    "status", "success",
                    "connections", connections
            );
        }
    }

    // Helper methods
    private static boolean connectionExists(Connection conn, String user1, String user2) throws SQLException {
        String sql = "SELECT 1 FROM connections WHERE (userId = ? AND connectedUserId = ?) OR (userId = ? AND connectedUserId = ?) LIMIT 1";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user1);
            stmt.setString(2, user2);
            stmt.setString(3, user2);
            stmt.setString(4, user1);
            return stmt.executeQuery().next();
        }
    }

    private static String getRequesters(Connection conn, String userId) throws SQLException {
        String sql = "SELECT requesters FROM connectRequests WHERE userId = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getString("requesters") : "";
        }
    }

    private static void updateRequesters(Connection conn, String userId, String requesters) throws SQLException {
        String sql = "INSERT OR REPLACE INTO connectRequests (userId, requesters) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setString(2, requesters);
            stmt.executeUpdate();
        }
    }

    private static void createConnection(Connection conn, String user1, String user2) throws SQLException {
        String sql = "INSERT OR IGNORE INTO connections (userId, connectedUserId) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user1);
            stmt.setString(2, user2);
            stmt.executeUpdate();
        }
    }
}
