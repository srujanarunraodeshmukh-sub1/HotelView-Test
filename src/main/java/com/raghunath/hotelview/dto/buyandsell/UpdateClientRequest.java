package com.raghunath.hotelview.dto.buyandsell;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateClientRequest {
    private String name;
    private String mobile;
    private String requiredModel;
    private Double expectedPrice;
    private String color;
    private Integer km;
    private Integer year;
    private String status; // e.g., "PENDING", "FULFILLED", "CANCELLED"
}