package com.raghunath.hotelview.repository.buyandsell;

import com.raghunath.hotelview.entity.buyandsell.Item;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemRepository extends MongoRepository<Item, String> {

    // 1. Fetch all items for a dealer
    List<Item> findByDealerId(String dealerId);

    // 2. Fetch items updated after a specific version
    List<Item> findByDealerIdAndVersionGreaterThan(String dealerId, long version);

    // 3. Count active (non-deleted) items for a dealer
    long countByDealerIdAndIsDeletedFalse(String dealerId);
}