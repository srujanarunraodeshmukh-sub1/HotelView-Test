package com.raghunath.hotelview.repository.restaurantandinstant;

import com.raghunath.hotelview.entity.restaurantandinstant.CustomerDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerDetailsRepository extends MongoRepository<CustomerDetails, String> {
    // Spring Data automatically handles the pagination and sorting based on Pageable
    Page<CustomerDetails> findByHotelId(String hotelId, Pageable pageable);
}