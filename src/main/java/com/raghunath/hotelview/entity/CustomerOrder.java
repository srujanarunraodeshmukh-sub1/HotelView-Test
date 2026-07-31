package com.raghunath.hotelview.entity;

import com.raghunath.hotelview.dto.admin.OrderItem;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndex(name = "user_hotel_table_status", def = "{'hotelId': 1, 'tableName': 1, 'status': 1}")
@Document(collection = "customer_table_orders")
public class CustomerOrder {
    @Id
    private String id;

    @Field("hotelId")
    private String hotelId;

    @Field("tableName")
    private String tableName;

    @Field("orderType")
    private String orderType;

    private List<OrderItem> items;
    private Double totalAmount;
    private String status; // "PENDING_ACCEPTANCE", "ACCEPTED", "REJECTED"
    private String comments;

    private String placedBy;         // Set to "CUSTOMER"
    private String editedBy;         // Waiter ID if modified
    private String employeeAcceptedBy; // Waiter ID who approved the QR order
    private String acceptedBy;       // Reserved for CHEF in kitchen
    private String completedBy;

    private String createdDate;
    private String createdTime;

    @Field("updatedAt")
    private LocalDateTime updatedAt;
}