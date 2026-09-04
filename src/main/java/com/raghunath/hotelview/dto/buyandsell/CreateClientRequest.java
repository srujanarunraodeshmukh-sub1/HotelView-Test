package com.raghunath.hotelview.dto.buyandsell;

import lombok.Data;

@Data
public class CreateClientRequest {
    // Required identity fields (optional to validate or omit as needed)
    private String name;
    private String mobile;

    // Optional vehicle preference fields
    private String requiredModel;
    private Double expectedPrice;
    private String color;
    private Integer km;
    private Integer year;
}