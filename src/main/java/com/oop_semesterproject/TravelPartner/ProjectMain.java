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
//user defined Imports
import com.oop_semesterproject.TravelPartner.DTO.*;
import com.oop_semesterproject.TravelPartner.exceptions.*;

public class ProjectMain {

    public static void main(String[] args) {
        Javalin app = Javalin.create(
                config -> {
                    config.plugins.enableCors(cors -> {
                        cors.add(it -> {
                            it.anyHost();  //TODO Don't use this in production
                        });
                    });
                }
        ).start(7000);
        Database.initializeDatabase();
        ObjectMapper objectMapper = new ObjectMapper();
        // for testing on local host
        app.before(ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            ctx.header("Access-Control-Allow-Headers", "*");
        });

        // Auth routes
        /// Registration of a new User
        app.post("/api/register", ctx -> {
            try {
                // Parse directly to UserRequest
                UserRegisterRequest userRequest = objectMapper.readValue(ctx.body(), UserRegisterRequest.class);

                // Validate required fields
                if (userRequest.name() == null
                        || userRequest.email() == null
                        || userRequest.password() == null
                        || userRequest.phone_number() == null
                        || userRequest.transportationType() == null
                        || userRequest.available() == null) {
                    throw new IncompleteInformationException("Required fields are not present/incorrect");
                }

                Map<String, Object> result = Database.createUser(userRequest);
                ctx.status(201).json(result);

            } catch (IncompleteInformationException | EmailAlreadyExistsException e) {
                ctx.status(500).json(Map.of(
                        "status", "error",
                        "message", "Registration error",
                        "details", e.getMessage()
                ));
            } catch (JsonProcessingException e) {
                ctx.status(400).json(Map.of(
                        "status", "error",
                        "message", "Invalid JSON format"
                //      ,"details", e.getMessage()
                ));
            } catch (SQLException e) {
                ctx.status(500).json(Map.of(
                        "status", "error",
                        "message", "Database error",
                        "details", e.getMessage()
                ));
            }
        });
        //login the user
        app.post("/api/login", ctx -> {
            try {
                var body = objectMapper.readValue(ctx.body(), Map.class);

                String email = (String) body.get("email");
                String password = (String) body.get("password");

                LoginResponse result = Database.loginUser(email, password);
                ctx.status(200).json(result);

            } catch (JsonProcessingException e) {
                ctx.status(400).json(Map.of(
                        "status", "error",
                        "message", "Invalid JSON format"
                //      ",details", e.getMessage()
                ));
            } catch (UserNotFoundException | InvalidCredentialsException e) {

                ctx.status(401).json(Map.of("error", e.getMessage()));

            } catch (SQLException e) {
                ctx.status(500).json(Map.of(
                        "error", "Database error",
                        "message", e.getMessage() //TODO: delete in production
                ));
            }
        });
        //add route for the user to or from uni route
        app.post("/api/addroute", ctx -> {

            try {
                //auth confirmation
                String userId = JWTUtil.requireAuth(ctx);
                // JSON parsing
                AddRouteRequest route = objectMapper.readValue(ctx.body(), AddRouteRequest.class);
                // Main Processing function call
                Map<String, Object> result = Database.addRoute(route, userId);

                ctx.status(200).json(result);
            } catch (UnauthorizedException e) {
                ctx.status(400);
            } catch (JsonProcessingException e) {
                ctx.status(400).json(Map.of(
                        "status", "error",
                        "message", "Invalid JSON format"
                //      ,"details", e.getMessage()
                ));
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("error", e.getMessage()));
            } catch (SQLException e) {
                if (e.getMessage().contains("UNIQUE") || e.getMessage().contains("constraint failed")) {

                    ctx.status(500).json(Map.of("error", "Database error", "details", "Route Already Exists, One to and one from route is allowed per user"));

                } else {
                    ctx.status(500).json(Map.of("error", "Database error", "details", e.getMessage()));
                }

            }
        });

        app.post("/api/routes", ctx -> {
            try {
                RouteLookupReponse req = objectMapper.readValue(ctx.body(), RouteLookupReponse.class);
                List<RouteResult> results = Database.lookupRoutes(req);
                ctx.status(200).json(Map.of(
                        "status", "success",
                        "routes", results,
                        "count", results.size(),
                        "page", req.page()
                ));
            } catch (IllegalArgumentException e) {
                ctx.status(400).json(Map.of("error", e.getMessage()));
            } catch (SQLException e) {
                ctx.status(500).json(Map.of("error", "Database error", "details", e.getMessage()));
            }
        });

        app.get("/api/user/{userId}", ctx -> {
            try {
                // Authentication
                String authenticatedUserId = JWTUtil.requireAuth(ctx);

                // Get target user ID from path
                String targetUserId = ctx.pathParam("userId");

                // Fetch details with security check
                UserDetailsResponse response = Database.getUserDetails(
                        authenticatedUserId,
                        targetUserId
                );

                ctx.json(response);

            } catch (UnauthorizedException e) {
                ctx.status(401).json(Map.of("error", "Unauthorized"));
            } catch (IllegalArgumentException e) {
                ctx.status(403).json(Map.of(
                        "error", "Forbidden",
                        "message", e.getMessage()
                ));
            } catch (SQLException e) {
                ctx.status(500).json(Map.of("error", "Database error"));
            }
        });

        //DEBUG: Fetch all 
        app.get("/users", ctx -> {

            try {
                String userId = JWTUtil.requireAuth(ctx);

                try (Connection conn = DriverManager.getConnection("jdbc:sqlite:root.db")) {
                    var rs = conn.createStatement().executeQuery("SELECT * FROM users");
                    StringBuilder result = new StringBuilder();
                    while (rs.next()) {
                        result.append(rs.getString("id")).append(": ").append(rs.getString("name")).append("\n");
                    }
                    ctx.result(result.toString());
                } catch (SQLException e) {
                    ctx.status(500);
                }
            } catch (UnauthorizedException e) {
                ctx.status(400);
            }
        });
// Send connection request
        app.post("/api/connect/request", (ctx) -> {
            try {
                String userId = JWTUtil.requireAuth(ctx);
                ConnectionRequest request = objectMapper.readValue(ctx.body(), ConnectionRequest.class);
                if (userId.equals(request.targetUserId())) {
                    throw new IllegalArgumentException("Can't send request to yourself!");
                } else {
                    Map<String, Object> result = ConnectionManager.sendRequest(
                            userId,
                            request.targetUserId()
                    );
                    ctx.status(200).json(result);
                }
            } catch (IllegalArgumentException e) {
                ctx.status(403).json(Map.of("error", "Forbidden", "message", e.getMessage()));
            } catch (UnauthorizedException e) {
                ctx.status(401);
            } catch (JsonProcessingException e) {
                ctx.status(400).json(Map.of("error", "Invalid JSON"));
            } catch (SQLException e) {
                ctx.status(500).json(Map.of("error", "Database error"));
            }
        });

// Accept connection request
        app.post("/api/connect/accept", ctx -> {
            try {
                String userId = JWTUtil.requireAuth(ctx);
                ConnectionActionRequest request = objectMapper.readValue(ctx.body(), ConnectionActionRequest.class);
                if (request.requesterId().equals(userId)) {
                    throw new IllegalArgumentException("Can't accept request of yourself!");
                }
                Map<String, Object> result = ConnectionManager.acceptRequest(
                        userId,
                        request.requesterId()
                );

                ctx.status(200).json(result);
            } catch (IllegalArgumentException e) {
                ctx.status(403).json(Map.of("error", "Forbidden", "message", e.getMessage()));
            } catch (UnauthorizedException e) {
                ctx.status(401);
            } catch (JsonProcessingException e) {
                ctx.status(400).json(Map.of("error", "Invalid JSON"));
            } catch (SQLException e) {
                ctx.status(500).json(Map.of("error", "Database error"));
            }
        });

// Get pending requests
        app.get("/api/connect/requests", ctx -> {
            try {
                String userId = JWTUtil.requireAuth(ctx);
                Map<String, Object> result = ConnectionManager.getRequests(userId);
                ctx.status(200).json(result);
            } catch (UnauthorizedException e) {
                ctx.status(401);
            } catch (SQLException e) {
                ctx.status(500).json(Map.of("error", "Database error"));
            }
        });

// Get connections
        app.get("/api/connect/list", ctx -> {
            try {
                String userId = JWTUtil.requireAuth(ctx);
                Map<String, Object> result = ConnectionManager.getConnections(userId);
                ctx.status(200).json(result);
            } catch (UnauthorizedException e) {
                ctx.status(401);
            } catch (SQLException e) {
                ctx.status(500).json(Map.of("error", "Database error"));
            }
        });

        app.delete("/api/connection/{userId}", ctx -> {
            try {
                String authenticatedUserId = JWTUtil.requireAuth(ctx);
                String targetUserId = ctx.pathParam("userId");

                try (Connection conn = DriverManager.getConnection("jdbc:sqlite:root.db")) {
                    conn.setAutoCommit(false);

                    // Delete bidirectional connections
                    String deleteSql = "DELETE FROM connections WHERE (userId = ? AND connectedUserId = ?) OR (userId = ? AND connectedUserId = ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                        stmt.setString(1, authenticatedUserId);
                        stmt.setString(2, targetUserId);
                        stmt.setString(3, targetUserId);
                        stmt.setString(4, authenticatedUserId);
                        int deleted = stmt.executeUpdate();

                        if (deleted == 0) {
                            ctx.status(404).json(Map.of("error", "Connection not found"));
                        } else {
                            conn.commit();
                            ctx.status(200).json(Map.of(
                                    "status", "success",
                                    "message", "Connection deleted"
                            ));
                        }
                    }
                }
            } catch (UnauthorizedException e) {
                ctx.status(401);
            } catch (SQLException e) {
                ctx.status(500).json(Map.of("error", "Database error"));
            }
        });

    }
}
