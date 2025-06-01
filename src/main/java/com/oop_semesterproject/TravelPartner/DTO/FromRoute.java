package com.oop_semesterproject.TravelPartner.DTO;
/**
 *
 * @author pc
 */
public record FromRoute(
    String startPoint,
    String endPoint,
    String startTimestamp,
    String endAddress,
    String generalArea
) {}

