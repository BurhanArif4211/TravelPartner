package com.oop_semesterproject.TravelPartner.DTO;
/**
 *
 * @author pc
 */
public record ToRoute(
    String startPoint,
    String endPoint,
    String startTimestamp,
    String startAddress,
    String generalArea
) {}
