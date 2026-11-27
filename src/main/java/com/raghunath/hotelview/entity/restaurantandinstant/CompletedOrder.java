package com.raghunath.hotelview.entity.restaurantandinstant;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Document(collection = "completed_orders")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompletedOrder {
    @Id
    private String id;
    private String hotelId;
    private String orderType;
    private String tableName;

    private String customerName;
    private String customerMobile;
    private String customerAddress;

    private List<KitchenOrderSnapshot> allOrders;

    private Double grandTotal;
    private String paymentStatus;
    private Double discountPercent;
    private Double discountAmount;
    private Double totalPayable;

    private Map<String, Double> gstDetails;
    private Map<String, Double> extraCharges;

    private Double lastTotalPayable;

    private List<Map<String, Object>> gsttype;

    private String checkoutBy;
    private String checkoutDate;
    private String checkoutTime;

    // Identifies the original credit bill.
    // Null for ordinary restaurant and instant checkout orders.
    private String creditBillId;

    private Double paidAmount;
    private Double pendingAmount;
    private LocalDate billDate;
}
