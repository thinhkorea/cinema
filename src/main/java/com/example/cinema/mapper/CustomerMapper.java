package com.example.cinema.mapper;

import com.example.cinema.domain.Customer;
import com.example.cinema.domain.User;
import com.example.cinema.dto.CustomerResponse;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public CustomerResponse toResponse(Customer customer) {
        User user = customer.getUser();
        return new CustomerResponse(
                customer.getCustomerId(),
                user == null ? null : user.getUserId(),
                user == null ? null : user.getEmail(),
                user == null ? null : user.getPhone(),
                user == null ? null : user.getFullName(),
                customer.getAddress(),
                customer.getGender(),
                customer.getLoyaltyPoints()
        );
    }
}
