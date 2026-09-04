package com.raghunath.hotelview.service.buyandsell;

import com.raghunath.hotelview.dto.buyandsell.CreateClientRequest;
import com.raghunath.hotelview.dto.buyandsell.SyncInfoDTO;
import com.raghunath.hotelview.entity.buyandsell.ClientRequirement;
import com.raghunath.hotelview.repository.buyandsell.ClientRequirementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ClientService {

    @Autowired
    private ClientRequirementRepository clientRepository;

    @Autowired
    private DeltaSyncService deltaSyncService;

    public ClientRequirement createClient(String dealerId, CreateClientRequest request) {
        long nextVersion = deltaSyncService.getNextClientVersion(dealerId);

        ClientRequirement client = new ClientRequirement();
        client.setDealerId(dealerId);
        client.setName(request.getName());
        client.setMobile(request.getMobile());
        client.setRequiredModel(request.getRequiredModel());
        client.setExpectedPrice(request.getExpectedPrice());
        client.setColor(request.getColor());
        client.setKm(request.getKm());
        client.setYear(request.getYear());
        client.setStatus("PENDING");
        client.setCreatedAt(LocalDateTime.now());
        client.setVersion(nextVersion);
        client.setDeleted(false);

        return clientRepository.save(client);
    }

    public SyncInfoDTO getClientSyncInfo(String dealerId) {
        long version = deltaSyncService.getCurrentClientVersion(dealerId);
        long count = clientRepository.countByDealerIdAndIsDeletedFalse(dealerId);
        return new SyncInfoDTO(version, count);
    }

    public List<ClientRequirement> getClients(String dealerId, Long sinceVersion) {
        if (sinceVersion != null && sinceVersion > 0) {
            return clientRepository.findByDealerIdAndVersionGreaterThan(dealerId, sinceVersion);
        }
        return clientRepository.findByDealerId(dealerId);
    }
}