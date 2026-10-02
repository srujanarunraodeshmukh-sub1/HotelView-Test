package com.raghunath.hotelview.repository.restaurantandinstant;

import com.raghunath.hotelview.entity.restaurantandinstant.CustomerOrder;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CustomerOrderRepository extends MongoRepository<CustomerOrder, String> {

    // Query strictly matching hotelId and tableId (mapped via tableName field)
    List<CustomerOrder> findByHotelIdAndTableNameAndStatusNotOrderByCreatedDateDescCreatedTimeDesc(
            String hotelId, String tableId, String status);

    List<CustomerOrder> findByHotelIdAndTableNameAndStatus(
            String hotelId,
            String tableName,
            String status
    );

    List<CustomerOrder> findByHotelIdAndStatusOrderByCreatedDateDescCreatedTimeDesc(
            String hotelId, String status);
}
