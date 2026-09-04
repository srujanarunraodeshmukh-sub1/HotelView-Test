package com.raghunath.hotelview.repository.buyandsell;

import com.raghunath.hotelview.entity.buyandsell.ClientRequirement;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ClientRequirementRepository extends MongoRepository<ClientRequirement, String> {
    List<ClientRequirement> findByDealerId(String dealerId);
    List<ClientRequirement> findByDealerIdAndVersionGreaterThan(String dealerId, long version);
    long countByDealerIdAndIsDeletedFalse(String dealerId);
}