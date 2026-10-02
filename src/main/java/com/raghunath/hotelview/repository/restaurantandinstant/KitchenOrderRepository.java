package com.raghunath.hotelview.repository.restaurantandinstant;

import com.raghunath.hotelview.entity.restaurantandinstant.KitchenOrder;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface KitchenOrderRepository extends MongoRepository<KitchenOrder, String> {

    List<KitchenOrder> findByHotelIdAndStatusIn(String hotelId, List<String> statuses);

    // ✅ FIXED: Changed 'OrderByCreatedAtDesc' to 'OrderByCreatedDateDescCreatedTimeDesc'
    List<KitchenOrder> findByHotelIdAndTableNameAndStatusNotOrderByCreatedDateDescCreatedTimeDesc(
            String hotelId, String tableName, String status);

    KitchenOrder findFirstByHotelIdAndTableNameOrderByCreatedDateDescCreatedTimeDesc(
            String hotelId,
            String tableName
    );

    List<KitchenOrder> findByHotelIdAndStatus(String hotelId, String status);

    Long countByHotelIdAndOrderTypeAndStatus(String hotelId, String orderType, String status);
    Long countByHotelIdAndOrderTypeAndCreatedDate(String hotelId, String orderType, String createdDate);

    long countByHotelIdAndTableNameAndStatusNot(
            String hotelId, String tableName, String status);

    // ✅ FIXED: Changed 'OrderByCreatedAtDesc' to 'OrderByCreatedDateDescCreatedTimeDesc'
    List<KitchenOrder> findByHotelIdAndTableNameAndUpdatedAtAfterOrderByCreatedDateDescCreatedTimeDesc(
            String hotelId, String tableName, LocalDateTime updatedAt);

    List<KitchenOrder> findByHotelIdAndTableName(String hotelId, String fromTable);
}