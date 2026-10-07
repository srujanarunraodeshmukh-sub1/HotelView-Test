package com.raghunath.hotelview.repository.restaurantandinstant;

import com.raghunath.hotelview.entity.restaurantandinstant.InstantCreditCustomer;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface InstantCreditCustomerRepository
        extends MongoRepository<InstantCreditCustomer, String> {

    Optional<InstantCreditCustomer> findByIdAndHotelId(
            String id,
            String hotelId
    );

    Optional<InstantCreditCustomer> findByHotelIdAndMobile(
            String hotelId,
            String mobile
    );

    List<InstantCreditCustomer> findByHotelIdAndActiveTrueOrderByNameAsc(
            String hotelId
    );
}