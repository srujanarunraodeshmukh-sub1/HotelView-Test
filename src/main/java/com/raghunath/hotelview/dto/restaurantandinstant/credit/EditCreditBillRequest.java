package com.raghunath.hotelview.dto.restaurantandinstant.credit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class EditCreditBillRequest {

    @NotNull(message = "Total amount is required")
    @PositiveOrZero(message = "Total amount cannot be negative")
    private Double totalAmount;

    @NotNull(message = "Paid amount is required")
    @PositiveOrZero(message = "Paid amount cannot be negative")
    private Double paidAmount;

    private String editReason;

    @Valid
    private List<CommodityRequest> commodities;
}