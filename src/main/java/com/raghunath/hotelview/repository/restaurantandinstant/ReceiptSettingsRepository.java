package com.raghunath.hotelview.repository.restaurantandinstant;

import com.raghunath.hotelview.entity.restaurantandinstant.ReceiptSettings;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReceiptSettingsRepository extends MongoRepository<ReceiptSettings, String> {
    Optional<ReceiptSettings> findByHotelIdAndReceiptType(String hotelId, String receiptType);
}