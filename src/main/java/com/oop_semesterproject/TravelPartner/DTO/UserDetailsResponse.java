    package com.oop_semesterproject.TravelPartner.DTO;

/**
 *
 * @author pc
 */
public record UserDetailsResponse(
    String name,
    String phoneNumber,
    String transportationType,
    String available,
    ToRoute toRoute,
    FromRoute fromRoute
) {}
