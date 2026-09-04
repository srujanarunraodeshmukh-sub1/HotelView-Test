package com.raghunath.hotelview.service.buyandsell;


import com.raghunath.hotelview.entity.buyandsell.DealerDataVersion;
import com.raghunath.hotelview.repository.buyandsell.DealerDataVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DeltaSyncService {

    @Autowired
    private DealerDataVersionRepository versionRepository;

    public synchronized long getNextClientVersion(String dealerId) {
        DealerDataVersion v = versionRepository.findByDealerId(dealerId)
                .orElseGet(() -> {
                    DealerDataVersion newV = new DealerDataVersion();
                    newV.setDealerId(dealerId);
                    return newV;
                });
        v.setClientVersion(v.getClientVersion() + 1);
        versionRepository.save(v);
        return v.getClientVersion();
    }

    public synchronized long getNextItemVersion(String dealerId) {
        DealerDataVersion v = versionRepository.findByDealerId(dealerId)
                .orElseGet(() -> {
                    DealerDataVersion newV = new DealerDataVersion();
                    newV.setDealerId(dealerId);
                    return newV;
                });
        v.setItemVersion(v.getItemVersion() + 1);
        versionRepository.save(v);
        return v.getItemVersion();
    }

    public long getCurrentClientVersion(String dealerId) {
        return versionRepository.findByDealerId(dealerId)
                .map(DealerDataVersion::getClientVersion).orElse(0L);
    }

    public long getCurrentItemVersion(String dealerId) {
        return versionRepository.findByDealerId(dealerId)
                .map(DealerDataVersion::getItemVersion).orElse(0L);
    }
}