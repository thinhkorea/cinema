package com.example.cinema.exception;

import com.example.cinema.constant.AppConstants;

public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(Long id) {
        super(AppConstants.CUSTOMER_NOT_FOUND + ": " + id);
    }
}
