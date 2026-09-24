package com.raghunath.hotelview.repository.customer;

import com.raghunath.hotelview.entity.customer.CustomerRefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CustomerRefreshTokenRepository extends MongoRepository<CustomerRefreshToken, String> {
    Optional<CustomerRefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);
    void deleteByCustomerId(String customerId); // handy for a future "logout all devices"
}