package com.raghunath.hotelview.dto.restaurantandinstant;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class CompletedOrderEditDTO {
    private Double grandTotal;
    private Double discountPercent;     // Optional override
    private Double discountAmount;
    private Double customTotalPayable;
    private List<Map<String, Object>> gsttype;
    private List<UpdatedItemPayload> items;

    @Data
    public static class UpdatedItemPayload {
        private String itemId;
        private String itemName;
        private String quantity;
        private Double price;
        private String unit;
        private String gst;
        private Double preSubTotal;
        private Double subTotal;
    }
}