package com.oop_semesterproject.TravelPartner.DTO;
/**
 *
 * @author pc
 */
public record AddRouteRequest(         
String type,             
String startPoint,
String endPoint,
String startTimestamp,
String address,
String generalArea
        ) {}
