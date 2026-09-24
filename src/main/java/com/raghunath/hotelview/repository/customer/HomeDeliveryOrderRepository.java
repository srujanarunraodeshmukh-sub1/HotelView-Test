package com.raghunath.hotelview.repository.customer;

import com.raghunath.hotelview.entity.customer.HomeDeliveryOrder;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface HomeDeliveryOrderRepository extends MongoRepository<HomeDeliveryOrder, String> {
    List<HomeDeliveryOrder> findByHotelIdAndStatus(String hotelId, String status);
}