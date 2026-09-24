package com.raghunath.hotelview.entity.customer;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "customer_refresh_tokens")
public class CustomerRefreshToken {

    @Id
    private String id;

    @Indexed
    private String customerId;

    @Indexed(unique = true)
    private String tokenHash; // SHA-256 hash — never store the raw token

    private String deviceInfo; // optional: app sends device model / app version

    private LocalDateTime createdAt;

    @Indexed(expireAfterSeconds = 0) // MongoDB TTL index — auto-deletes expired rows
    private Date expiresAt;

    private boolean revoked;
}