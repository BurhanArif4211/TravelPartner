package com.oop_semesterproject.TravelPartner.DTO;

/**
 *
 * @author pc
 */
public record LoginResponse(
    String status,
    String id,
    String name,
    String email,
    String phone_number,
    String token
) {}

