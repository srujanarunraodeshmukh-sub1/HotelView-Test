package com.raghunath.hotelview.repository.buyandsell;

import com.raghunath.hotelview.entity.buyandsell.Insurance;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface InsuranceRepository extends MongoRepository<Insurance, String> {
    List<Insurance> findByDealerId(String dealerId);
    List<Insurance> findByDealerIdAndVersionGreaterThan(String dealerId, Long version);
    long countByDealerIdAndDeletedFalse(String dealerId);
}
