package com.raghunath.hotelview.entity.customer;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "customer_users")
public class CustomerUser {

    @Id
    private String id;

    @Indexed(unique = true)
    private String mobileNumber;

    private String name; // nullable — filled in when profile is built later

    @Builder.Default
    private String role = "CUSTOMER";

    @Builder.Default
    private boolean active = true;

    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
}