package com.raghunath.hotelview.repository.restaurantandinstant;

import com.raghunath.hotelview.entity.restaurantandinstant.Admin;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface AdminRepository extends MongoRepository<Admin, String> {

    Optional<Admin> findByMobile(String mobile);

    // Change <Object> to <Admin> here 👇
    Optional<Admin> findByHotelId(String hotelId);
}