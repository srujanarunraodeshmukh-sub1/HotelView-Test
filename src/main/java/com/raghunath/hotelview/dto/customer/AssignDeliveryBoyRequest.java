package com.raghunath.hotelview.dto.customer;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AssignDeliveryBoyRequest {

    @NotBlank(message = "Delivery boy name is required")
    private String name;

    @NotBlank(message = "Delivery boy mobile is required")
    private String mobile;

    private String vehicleNumber;

    private String staffId; // employee/staff reference, if the delivery boy is on payroll
}