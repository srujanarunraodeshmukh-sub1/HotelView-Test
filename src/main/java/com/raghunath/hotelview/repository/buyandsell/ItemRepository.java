package com.raghunath.hotelview.repository.buyandsell;

import com.raghunath.hotelview.entity.buyandsell.Item;
import com.raghunath.hotelview.entity.buyandsell.CategoryType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemRepository extends MongoRepository<Item, String> {
    List<Item> findByDealerId(String dealerId);
    List<Item> findByDealerIdAndCategoryType(String dealerId, CategoryType categoryType);
}