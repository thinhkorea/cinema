package com.example.cinema.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer")
public class CustomerController {

    @GetMapping("/profile")
    public String getProfile() {
        return "Welcome CUSTOMER! Đây là thông tin hồ sơ khách hàng.";
    }
}
