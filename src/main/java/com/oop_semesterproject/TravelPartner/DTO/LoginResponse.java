/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
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

