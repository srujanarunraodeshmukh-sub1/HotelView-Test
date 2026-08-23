package com.raghunath.hotelview.repository.restaurantandinstant;

import com.raghunath.hotelview.entity.restaurantandinstant.MenuItem;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository // 🌟 REQUIRED ANNOTATION
public interface UserMenuItemRepository extends MongoRepository<MenuItem, String> {

    // Fetches all available items for a specific hotel ID
    List<MenuItem> findByHotelIdAndIsAvailableTrue(String hotelId);

    // Fallback: Fetch all menu items for the hotel ID
    List<MenuItem> findByHotelId(String hotelId);
}