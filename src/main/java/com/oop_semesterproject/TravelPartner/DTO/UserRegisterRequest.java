package com.oop_semesterproject.TravelPartner.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 *
 * @author archi
 */
// UserRequest.java (record - Java 16+)
public record UserRegisterRequest(
        String available,
        String transportationType,
        String name,
        String email,
        String password,
        @JsonProperty("phone_number")
        String phone_number
        ) {

}
