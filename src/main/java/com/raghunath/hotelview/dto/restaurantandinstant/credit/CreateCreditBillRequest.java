package com.raghunath.hotelview.dto.restaurantandinstant.credit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateCreditBillRequest {

    /*
     * Existing customer.
     *
     * If null/blank:
     * create a new customer.
     */
    private String customerId;

    /*
     * Required when customerId is not supplied.
     */
    private String name;

    @NotBlank
    private String mobile;

    private String address;

    @NotNull(message = "Total amount is required")
    @PositiveOrZero(message = "Total amount cannot be negative")
    private Double totalAmount;

    @NotNull(message = "Paid amount is required")
    @PositiveOrZero(message = "Paid amount cannot be negative")
    private Double paidAmount;

    @Valid
    private List<CommodityRequest> commodities;
}