package com.example.cinema.dto;

import com.example.cinema.domain.Customer;

public record CustomerResponse(
        Long customerId,
        Long userId,
        String email,
        String phone,
        String fullName,
        String address,
        Customer.Gender gender,
        Integer loyaltyPoints
) {
}
