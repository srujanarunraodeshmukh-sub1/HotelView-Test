package com.raghunath.hotelview.repository.customer;

import com.raghunath.hotelview.entity.customer.DeliveryBoyRefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface DeliveryBoyRefreshTokenRepository extends MongoRepository<DeliveryBoyRefreshToken, String> {
    Optional<DeliveryBoyRefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);
    void deleteByDeliveryBoyId(String deliveryBoyId);
}