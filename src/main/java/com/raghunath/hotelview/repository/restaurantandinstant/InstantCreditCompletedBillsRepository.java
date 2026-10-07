package com.raghunath.hotelview.repository.restaurantandinstant;

import com.raghunath.hotelview.entity.restaurantandinstant.InstantCreditCompletedBills;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface InstantCreditCompletedBillsRepository
        extends MongoRepository<InstantCreditCompletedBills, String> {

    Optional<InstantCreditCompletedBills>
    findByHotelIdAndCustomerId(
            String hotelId,
            String customerId
    );

    List<InstantCreditCompletedBills>
    findByHotelIdOrderByUpdatedAtDesc(
            String hotelId
    );
}