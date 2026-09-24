package com.raghunath.hotelview.repository.customer;

import com.raghunath.hotelview.entity.customer.DeliveryBoyUser;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface DeliveryBoyUserRepository extends MongoRepository<DeliveryBoyUser, String> {
    Optional<DeliveryBoyUser> findByMobileNumber(String mobileNumber);
}