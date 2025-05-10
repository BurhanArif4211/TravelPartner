package com.oop_semesterproject.TravelPartner.DTO;

/**
 *
 * @author pc
 */
public record RouteLookupReponse(
    String type,             // "toRoute" or "fromRoute"
    String generalArea,      // optional filter
    int page,                // 1-based
    int limit                // max rows per page
) {}
