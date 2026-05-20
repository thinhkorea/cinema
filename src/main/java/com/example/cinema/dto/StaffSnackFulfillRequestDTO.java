package com.example.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StaffSnackFulfillRequestDTO {
    private Long popcornSnackId;
    private String additionalPaymentMethod;
}
