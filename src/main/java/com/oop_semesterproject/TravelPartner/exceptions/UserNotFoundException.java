package com.oop_semesterproject.TravelPartner.exceptions;

/**
 *
 * @author pc
 */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}