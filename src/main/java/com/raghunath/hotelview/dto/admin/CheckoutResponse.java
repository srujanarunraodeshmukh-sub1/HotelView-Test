package com.raghunath.hotelview.dto.admin;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class CheckoutResponse {
    private String id;
    private String shortId; // Last 6 digits
    private String checkoutDate;
    private String checkoutTime;
    private String orderType;
    private String tableName;
    private String customerName;
    private String customerMobile;
    private String customerAddress;
    private List<BillItem> items;
    private Double grandTotal;
    private Double totalPayable;
    private Double discountPercent;
    private Double discountAmount;
    private List<Map<String, Object>> gsttype;

    private String restaurantGstNo;

    private String restaurantName;
    private String restaurantAddress;
    private String restaurantContact;
    private String restaurantUpi;
    private String restaurantLogo;

    @Data
    @Builder
    public static class BillItem {
        private String itemName;
        private String quantity;
        private Double price; // unit price * quantity
        private String unit;
        private String gst;
        private Double preSubTotal;
        private Double subTotal;
    }
}