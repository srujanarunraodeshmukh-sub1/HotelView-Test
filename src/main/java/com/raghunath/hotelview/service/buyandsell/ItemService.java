package com.raghunath.hotelview.service.buyandsell;

import com.raghunath.hotelview.dto.buyandsell.AddItemRequest;
import com.raghunath.hotelview.dto.buyandsell.SyncInfoDTO;
import com.raghunath.hotelview.dto.buyandsell.UpdateItemRequest;
import com.raghunath.hotelview.entity.buyandsell.Item;
import com.raghunath.hotelview.entity.buyandsell.ItemStatus;
import com.raghunath.hotelview.repository.buyandsell.ItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ItemService {

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private DeltaSyncService deltaSyncService;

    public Item addItem(String dealerId, AddItemRequest request) {
        long nextVersion = deltaSyncService.getNextItemVersion(dealerId);

        Item item = new Item();
        item.setDealerId(dealerId);
        item.setCategoryId(request.getCategoryId());
        item.setCategoryType(String.valueOf(request.getCategoryType()));
        item.setTitle(request.getTitle());
        item.setSellingPrice(request.getSellingPrice());
        item.setCostPrice(request.getCostPrice());
        item.setStockQuantity(request.getStockQuantity());
        item.setCondition(request.getCondition());
        item.setImages(request.getImages());
        item.setStatus(String.valueOf(ItemStatus.AVAILABLE));
        item.setAttributes(request.getAttributes());
        item.setVersion(nextVersion);
        item.setDeleted(false);

        return itemRepository.save(item);
    }

    public Item updateItem(String dealerId, String itemId, UpdateItemRequest request) {
        Item existingItem = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found with id: " + itemId));

        if (!existingItem.getDealerId().equals(dealerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to edit this item.");
        }

        long nextVersion = deltaSyncService.getNextItemVersion(dealerId);
        existingItem.setVersion(nextVersion);

        if (request.getCategoryId() != null) existingItem.setCategoryId(request.getCategoryId());
        if (request.getCategoryType() != null) existingItem.setCategoryType(request.getCategoryType().name());
        if (request.getTitle() != null) existingItem.setTitle(request.getTitle());
        if (request.getSellingPrice() != null) existingItem.setSellingPrice(request.getSellingPrice());
        if (request.getCostPrice() != null) existingItem.setCostPrice(request.getCostPrice());
        if (request.getStockQuantity() != null) existingItem.setStockQuantity(request.getStockQuantity());
        if (request.getCondition() != null) existingItem.setCondition(request.getCondition());
        if (request.getImages() != null) existingItem.setImages(request.getImages());
        if (request.getStatus() != null) existingItem.setStatus(request.getStatus().name());

        if (request.getAttributes() != null && !request.getAttributes().isEmpty()) {
            if (existingItem.getAttributes() == null) {
                existingItem.setAttributes(request.getAttributes());
            } else {
                existingItem.getAttributes().putAll(request.getAttributes());
            }
        }

        return itemRepository.save(existingItem);
    }

    public SyncInfoDTO getItemSyncInfo(String dealerId) {
        long version = deltaSyncService.getCurrentItemVersion(dealerId);
        long count = itemRepository.countByDealerIdAndIsDeletedFalse(dealerId);
        return new SyncInfoDTO(version, count);
    }

    public List<Item> getItems(String dealerId, Long sinceVersion) {
        if (sinceVersion != null && sinceVersion > 0) {
            return itemRepository.findByDealerIdAndVersionGreaterThan(dealerId, sinceVersion);
        }
        return itemRepository.findByDealerId(dealerId);
    }
}