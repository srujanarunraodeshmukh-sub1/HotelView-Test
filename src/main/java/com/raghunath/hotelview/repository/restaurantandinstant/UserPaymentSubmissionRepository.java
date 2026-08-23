package com.raghunath.hotelview.repository.restaurantandinstant;

import com.raghunath.hotelview.entity.restaurantandinstant.UserPaymentSubmission;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserPaymentSubmissionRepository extends MongoRepository<UserPaymentSubmission, String> {
    // Use id instead of createdAt to avoid LocalDateTime issues
    Optional<UserPaymentSubmission> findTopByHotelIdOrderByIdDesc(String hotelId);
}