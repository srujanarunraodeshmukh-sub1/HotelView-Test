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
@Document(collection = "delivery_boy_refresh_tokens")
public class DeliveryBoyRefreshToken {

    @Id
    private String id;

    @Indexed
    private String deliveryBoyId;

    @Indexed(unique = true)
    private String tokenHash;

    private String deviceInfo;

    private LocalDateTime createdAt;

    @Indexed(expireAfterSeconds = 0)
    private Date expiresAt;

    private boolean revoked;
}