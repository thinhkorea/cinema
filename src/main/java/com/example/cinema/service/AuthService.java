package com.example.cinema.service;

import com.example.cinema.domain.Customer;
import com.example.cinema.domain.User;
import com.example.cinema.dto.RegisterRequest;
import com.example.cinema.repository.CustomerRepository;
import com.example.cinema.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    public void registerCustomer(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email đã tồn tại!");
        }

        if (userRepository.existsByPhone(req.getPhone())) {
            throw new IllegalArgumentException("Số điện thoại đã tồn tại!");
        }

        // Tạo User
        User user = new User();
        user.setEmail(req.getEmail());
        user.setPhone(req.getPhone());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setFullName(req.getFullName());
        user.setRole(User.Role.CUSTOMER);

        userRepository.save(user);

        // Tạo Customer
        Customer customer = new Customer();
        customer.setUser(user);
        customer.setAddress(req.getAddress());
        customer.setGender(
                req.getGender().equalsIgnoreCase("MALE")
                        ? Customer.Gender.MALE
                        : Customer.Gender.FEMALE);
        customerRepository.save(customer);
    }
}
