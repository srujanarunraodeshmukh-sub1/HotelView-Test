package com.raghunath.hotelview.repository.buyandsell;

import com.raghunath.hotelview.entity.buyandsell.CompletedDeal;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CompletedDealRepository extends MongoRepository<CompletedDeal, String> {
    List<CompletedDeal> findByDealerId(String dealerId);
    List<CompletedDeal> findByDealerIdAndVersionGreaterThan(String dealerId, Long version);
    long countByDealerIdAndDeletedFalse(String dealerId);
}