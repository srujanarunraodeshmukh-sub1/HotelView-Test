package com.raghunath.hotelview.dto.restaurantandinstant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminProfileDTO {
    private String name;
    private String mobile;
    private String alternateMobile;
    private List<String> email;
    private String restaurantUpi;
    private String address;
    private String subscriptionExpiry;
}