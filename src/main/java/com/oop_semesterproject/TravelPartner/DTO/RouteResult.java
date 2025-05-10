
package com.oop_semesterproject.TravelPartner.DTO;

/**
 *
 * @author pc
 */
public record RouteResult(
        String status,
    String userId,
    String startPoint,
    String endPoint,
    String startTimestamp,
    String address,
    String generalArea
) {}

