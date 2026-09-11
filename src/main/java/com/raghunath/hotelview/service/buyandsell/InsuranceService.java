package com.raghunath.hotelview.service.buyandsell;

import com.raghunath.hotelview.dto.buyandsell.CreateInsuranceRequest;
import com.raghunath.hotelview.dto.buyandsell.SyncInfoDTO;
import com.raghunath.hotelview.dto.buyandsell.UpdateInsuranceRequest;
import com.raghunath.hotelview.entity.buyandsell.Insurance;
import com.raghunath.hotelview.repository.buyandsell.InsuranceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InsuranceService {

    @Autowired
    private InsuranceRepository insuranceRepository;

    @Autowired
    private DeltaSyncService deltaSyncService;

    public Insurance createInsurance(String dealerId, CreateInsuranceRequest request){
        long nextVersion = deltaSyncService.getNextInsuranceVersion(dealerId);

        Insurance insurance = new Insurance();
        insurance.setDealerId(dealerId);
        insurance.setClientId(request.getClientId());

        if(request.getClient() != null){
            Insurance.ClientInfo client = new Insurance.ClientInfo();
            client.setName(request.getClient().getName());
            client.setMobile(request.getClient().getMobile());
            client.setAddress(request.getClient().getAddress());
            insurance.setClient(client);
        }

        if(request.getInsurance() != null){
            Insurance.InsuranceInfo info = new Insurance.InsuranceInfo();
            info.setName(request.getInsurance().getName());
            info.setMobile(request.getInsurance().getMobile());
            info.setLapseDate(request.getInsurance().getLapseDate());
            info.setVehicleNumber(request.getInsurance().getVehicleNumber());
            insurance.setInsurance(info);
        }

        insurance.setCreatedAt(LocalDateTime.now());
        insurance.setVersion(nextVersion);
        insurance.setDeleted(false);

        return insuranceRepository.save(insurance);
    }

    public Insurance updateInsurance(String dealerId, String insuranceId, UpdateInsuranceRequest request){
        Insurance existing = insuranceRepository.findById(insuranceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Insurance not found with id: " +insuranceId));

        if(!existing.getDealerId().equals(dealerId)){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "YOu do not have permission to edit this insurance record.");
        }

        long nextVersion = deltaSyncService.getNextInsuranceVersion(dealerId);
        existing.setVersion(nextVersion);

        if(request.getClientId() != null) existing.setClientId(request.getClientId());

        if(request.getClient() != null) {
            Insurance.ClientInfo client = existing.getClient() != null ? existing.getClient() : new Insurance.ClientInfo();
            if(request.getClient().getName() != null) client.setName(request.getClient().getName());
            if(request.getClient().getMobile() != null) client.setMobile(request.getClient().getMobile());
            if(request.getClient().getAddress() != null) client.setAddress(request.getClient().getAddress());
            existing.setClient(client);
        }

        if(request.getInsurance() != null) {
            Insurance.InsuranceInfo info = existing.getInsurance() != null ? existing.getInsurance() : new Insurance.InsuranceInfo();
            if(request.getInsurance().getName() != null) info.setName(request.getInsurance().getName());
            if(request.getInsurance().getMobile() != null) info.setMobile(request.getInsurance().getMobile());
            if(request.getInsurance().getLapseDate() != null) info.setLapseDate(request.getInsurance().getLapseDate());
            if(request.getInsurance().getVehicleNumber() != null) info.setVehicleNumber(request.getInsurance().getVehicleNumber());
            existing.setInsurance(info);
        }

        return insuranceRepository.save(existing);
    }

    public void deleteInsurance(String dealerId, String insuranceId){
        Insurance existing = insuranceRepository.findById(insuranceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Insurance not found with id:" +insuranceId));

        if(!existing.getDealerId().equals(dealerId)){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to delete this insurance record.");
        }

        long nextVersion = deltaSyncService.getNextInsuranceVersion(dealerId);
        existing.setVersion(nextVersion);
        existing.setDeleted(true);

        insuranceRepository.save(existing);
    }

    public SyncInfoDTO getInsuranceSyncInfo(String dealerId){
        long version = deltaSyncService.getCurrentInsuranceVersion(dealerId);
        long count = insuranceRepository.countByDealerIdAndDeletedFalse(dealerId);
        return new SyncInfoDTO(version, count);
    }

    public List<Insurance> getInsurances(String dealerId, Long sinceVersion){
        if(sinceVersion != null && sinceVersion > 0){
            return insuranceRepository.findByDealerIdAndVersionGreaterThan(dealerId, sinceVersion);
        }
        return insuranceRepository.findByDealerId(dealerId);
    }
}
