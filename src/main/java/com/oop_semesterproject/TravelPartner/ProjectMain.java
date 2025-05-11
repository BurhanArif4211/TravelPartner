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
                            it.anyHost();  // Don't use this in production
                            it.exposeHeader("hx-target");

                        });
                    });
                }//config in future
        ).start(7000);
        Database.initializeDatabase();
        ObjectMapper objectMapper = new ObjectMapper();
        // for testing on local host
        app.before(ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            ctx.header("Access-Control-Allow-Headers", "*");
            ctx.header("Access-Control-Allow-Credentials", "true"); // If using cookies
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

    }
}
