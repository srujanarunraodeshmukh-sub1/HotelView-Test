package com.raghunath.hotelview.dto.restaurantandinstant.credit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class EditCreditBillRequest {

    @NotNull(message = "Total amount is required")
    @PositiveOrZero(message = "Total amount cannot be negative")
    private Double totalAmount;

    @NotNull(message = "Paid amount is required")
    @PositiveOrZero(message = "Paid amount0 cannot be negative")
    private Double paidAmount;

    // GST fields
    @PositiveOrZero(message = "Gross total cannot be negative")
    private Double grossTotal;

    private Map<String, Double> extraCharges;

    private Map<String, Double> gstDetails;

    @PositiveOrZero(message = "Discount cannot be negative")
    private Double discount;

    private String editReason;

    @Valid
    private List<CommodityRequest> commodities;
}