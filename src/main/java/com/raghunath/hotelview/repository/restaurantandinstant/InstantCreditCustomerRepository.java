package com.raghunath.hotelview.repository.restaurantandinstant;

import com.raghunath.hotelview.entity.restaurantandinstant.InstantCreditCustomer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
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

    List<InstantCreditCustomer> findByHotelIdOrderByNameAsc(String hotelId);

    long countByHotelId(String hotelId);

    Optional<InstantCreditCustomer>
    findTopByHotelIdOrderByUpdatedAtDescIdDesc(
            String hotelId
    );

    @Query("{ 'hotelId': ?0, 'updatedAt': { '$gt': ?1, '$lte': ?2 } }")
    List<InstantCreditCustomer>
    findChangedCustomersBetween(
            String hotelId,
            LocalDateTime from,
            LocalDateTime to
    );

    Page<InstantCreditCustomer> findByHotelId(
            String hotelId,
            Pageable pageable
    );
}