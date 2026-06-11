package com.example.cinema.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StaffOptionDTO {
    private Long staffId;
    private String staffName;
    private String position;
}
