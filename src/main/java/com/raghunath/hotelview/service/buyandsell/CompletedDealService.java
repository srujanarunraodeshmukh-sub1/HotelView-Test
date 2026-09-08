package com.raghunath.hotelview.service.buyandsell;

import com.raghunath.hotelview.dto.buyandsell.MakeDealRequest;
import com.raghunath.hotelview.dto.buyandsell.SellItemRequest;
import com.raghunath.hotelview.dto.buyandsell.SyncInfoDTO;
import com.raghunath.hotelview.entity.buyandsell.ClientRequirement;
import com.raghunath.hotelview.entity.buyandsell.CompletedDeal;
import com.raghunath.hotelview.entity.buyandsell.Item;
import com.raghunath.hotelview.entity.buyandsell.ItemStatus;
import com.raghunath.hotelview.repository.buyandsell.ClientRequirementRepository;
import com.raghunath.hotelview.repository.buyandsell.CompletedDealRepository;
import com.raghunath.hotelview.repository.buyandsell.ItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CompletedDealService {

    @Autowired
    private CompletedDealRepository completedDealRepository;
    @Autowired
    private ClientRequirementRepository clientRequirementRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private DeltaSyncService deltaSyncService;

    public CompletedDeal sellItem(String dealerId, String itemId, SellItemRequest request) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found with id: " + itemId));

        if (!item.getDealerId().equals(dealerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to sell this item.");
        }

        if (String.valueOf(ItemStatus.SOLD).equals(item.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Item is already sold.");
        }

        long nextDealVersion = deltaSyncService.getNextCompletedDealVersion(dealerId);

        CompletedDeal deal = new CompletedDeal();
        deal.setDealerId(dealerId);
        deal.setItemId(item.getId());
        deal.setItem(item); // full snapshot at time of sale

        if (request.getClient() != null) {
            CompletedDeal.ClientInfo client = new CompletedDeal.ClientInfo();
            client.setName(request.getClient().getName());
            client.setMobile(request.getClient().getMobile());
            client.setAddress(request.getClient().getAddress());
            deal.setClient(client);
        }

        if (request.getInsurance() != null) {
            CompletedDeal.InsuranceInfo insurance = new CompletedDeal.InsuranceInfo();
            insurance.setName(request.getInsurance().getName());
            insurance.setMobile(request.getInsurance().getMobile());
            insurance.setLapseDate(request.getInsurance().getLapseDate());
            insurance.setVehicleNumber(request.getInsurance().getVehicleNumber());
            deal.setInsurance(insurance);
        }

        deal.setDealDate(LocalDateTime.now());
        deal.setVersion(nextDealVersion);
        deal.setDeleted(false);

        CompletedDeal savedDeal = completedDealRepository.save(deal);

        // mark the original item as sold so it drops out of active listings
        long nextItemVersion = deltaSyncService.getNextItemVersion(dealerId);
        item.setStatus(String.valueOf(ItemStatus.SOLD));
        item.setVersion(nextItemVersion);
        itemRepository.save(item);

        return savedDeal;
    }

    public CompletedDeal makeDeal(String dealerId, String clientId, MakeDealRequest request) {
        ClientRequirement clientRequirement = clientRequirementRepository.findById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client requirement not found with id: " + clientId));

        if (!clientRequirement.getDealerId().equals(dealerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to make a deal for this client.");
        }

        if (request.getItemId() == null || request.getItemId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "itemId is required to make a deal.");
        }

        Item item = itemRepository.findById(request.getItemId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found with id: " + request.getItemId()));

        if (!item.getDealerId().equals(dealerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to sell this item.");
        }

        if (String.valueOf(ItemStatus.SOLD).equals(item.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Item is already sold.");
        }

        long nextDealVersion = deltaSyncService.getNextCompletedDealVersion(dealerId);

        CompletedDeal deal = new CompletedDeal();
        deal.setDealerId(dealerId);
        deal.setItemId(item.getId());
        deal.setItem(item);
        deal.setClientId(clientRequirement.getId());

        CompletedDeal.ClientInfo client = new CompletedDeal.ClientInfo();
        client.setName(clientRequirement.getName());
        client.setMobile(clientRequirement.getMobile());
        client.setAddress(null); // ClientRequirement has no address field
        deal.setClient(client);

        if (request.getInsurance() != null) {
            CompletedDeal.InsuranceInfo insurance = new CompletedDeal.InsuranceInfo();
            insurance.setName(request.getInsurance().getName());
            insurance.setMobile(request.getInsurance().getMobile());
            insurance.setLapseDate(request.getInsurance().getLapseDate());
            insurance.setVehicleNumber(request.getInsurance().getVehicleNumber());
            deal.setInsurance(insurance);
        }

        deal.setDealDate(LocalDateTime.now());
        deal.setVersion(nextDealVersion);
        deal.setDeleted(false);

        CompletedDeal savedDeal = completedDealRepository.save(deal);

        // mark the item as sold
        long nextItemVersion = deltaSyncService.getNextItemVersion(dealerId);
        item.setStatus(String.valueOf(ItemStatus.SOLD));
        item.setVersion(nextItemVersion);
        itemRepository.save(item);

        // mark the client requirement as fulfilled
        long nextClientVersion = deltaSyncService.getNextClientVersion(dealerId);
        clientRequirement.setStatus("FULFILLED");
        clientRequirement.setVersion(nextClientVersion);
        clientRequirementRepository.save(clientRequirement);

        return savedDeal;
    }

    public SyncInfoDTO getCompletedDealSyncInfo(String dealerId) {
        long version = deltaSyncService.getCurrentCompletedDealVersion(dealerId);
        long count = completedDealRepository.countByDealerIdAndDeletedFalse(dealerId);
        return new SyncInfoDTO(version, count);
    }

    public List<CompletedDeal> getCompletedDeals(String dealerId, Long sinceVersion) {
        if (sinceVersion != null && sinceVersion > 0) {
            return completedDealRepository.findByDealerIdAndVersionGreaterThan(dealerId, sinceVersion);
        }
        return completedDealRepository.findByDealerId(dealerId);
    }
}