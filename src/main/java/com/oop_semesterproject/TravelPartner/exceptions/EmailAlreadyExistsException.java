package com.oop_semesterproject.TravelPartner.exceptions;

/**
 *
 * @author pc
 */
public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
