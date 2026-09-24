package com.raghunath.hotelview.repository.customer;

import com.raghunath.hotelview.entity.customer.CustomerUser;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CustomerUserRepository extends MongoRepository<CustomerUser, String> {
    Optional<CustomerUser> findByMobileNumber(String mobileNumber);
}