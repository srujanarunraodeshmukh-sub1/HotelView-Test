package com.raghunath.hotelview.repository.buyandsell;

import com.raghunath.hotelview.entity.buyandsell.DealerDataVersion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DealerDataVersionRepository extends MongoRepository<DealerDataVersion, String> {
    Optional<DealerDataVersion> findByDealerId(String dealerId);
}