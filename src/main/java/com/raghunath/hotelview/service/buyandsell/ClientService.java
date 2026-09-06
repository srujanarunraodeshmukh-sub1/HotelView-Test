package com.raghunath.hotelview.service.buyandsell;

import com.raghunath.hotelview.dto.buyandsell.CreateClientRequest;
import com.raghunath.hotelview.dto.buyandsell.SyncInfoDTO;
import com.raghunath.hotelview.dto.buyandsell.UpdateClientRequest;
import com.raghunath.hotelview.entity.buyandsell.ClientRequirement;
import com.raghunath.hotelview.repository.buyandsell.ClientRequirementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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

    public ClientRequirement updateClient(String dealerId, String clientId, UpdateClientRequest request) {
        ClientRequirement existingClient = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client requirement not found with id: " + clientId));

        if (!existingClient.getDealerId().equals(dealerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to edit this requirement.");
        }

        long nextVersion = deltaSyncService.getNextClientVersion(dealerId);
        existingClient.setVersion(nextVersion);

        if (request.getName() != null) existingClient.setName(request.getName());
        if (request.getMobile() != null) existingClient.setMobile(request.getMobile());
        if (request.getRequiredModel() != null) existingClient.setRequiredModel(request.getRequiredModel());
        if (request.getExpectedPrice() != null) existingClient.setExpectedPrice(request.getExpectedPrice());
        if (request.getColor() != null) existingClient.setColor(request.getColor());
        if (request.getKm() != null) existingClient.setKm(request.getKm());
        if (request.getYear() != null) existingClient.setYear(request.getYear());
        if (request.getStatus() != null) existingClient.setStatus(request.getStatus());

        return clientRepository.save(existingClient);
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