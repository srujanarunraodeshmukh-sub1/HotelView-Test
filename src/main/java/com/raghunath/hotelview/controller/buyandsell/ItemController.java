package com.raghunath.hotelview.controller.buyandsell;

import com.raghunath.hotelview.dto.buyandsell.AddItemRequest;
import com.raghunath.hotelview.dto.buyandsell.UpdateItemRequest;
import com.raghunath.hotelview.entity.buyandsell.Item;
import com.raghunath.hotelview.entity.buyandsell.ItemStatus;
import com.raghunath.hotelview.repository.buyandsell.ItemRepository;
import com.raghunath.hotelview.security.UserTypeAccessGuard;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/v1/buyandsell/items")
public class ItemController {

    private final ItemRepository itemRepository;
    private final UserTypeAccessGuard userTypeAccessGuard;

    public ItemController(ItemRepository itemRepository, UserTypeAccessGuard userTypeAccessGuard) {
        this.itemRepository = itemRepository;
        this.userTypeAccessGuard = userTypeAccessGuard;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> addItem(@Valid @RequestBody AddItemRequest request) {
        String currentDealerId = userTypeAccessGuard.enforceUserTypeForCurrentUser("BUYANDSELL");
        Item item = new Item();
        item.setDealerId(currentDealerId);
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

        Item savedItem = itemRepository.save(item);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Item added successfully!");
        response.put("data", savedItem);

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * @route   GET /api/v1/items
     * @desc    Fetch all inventory items for the logged-in dealer (extracted from access token)
     * @access  Private (Requires 'BUYANDSELL' account type)
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getDealerItems() {
        // 1. Authenticate user and ensure account type is 'BUYANDSELL'
        String currentDealerId = userTypeAccessGuard.enforceUserTypeForCurrentUser("BUYANDSELL");

        // 2. Fetch all items created by this specific dealer ID
        List<Item> dealerItems = itemRepository.findByDealerId(currentDealerId);

        // 3. Build response
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Items fetched successfully!");
        response.put("count", dealerItems.size());
        response.put("data", dealerItems);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * @route   PUT /api/v1/items/{id}
     * @desc    Edit an existing inventory item owned by the logged-in dealer
     * @access  Private (Requires 'BUYANDSELL' account type)
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateItem(
            @PathVariable("id") String itemId,
            @RequestBody UpdateItemRequest request) {

        // 1. Authenticate user from JWT token
        String currentDealerId = userTypeAccessGuard.enforceUserTypeForCurrentUser("BUYANDSELL");

        // 2. Fetch item from DB or throw 404
        Item existingItem = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found with id: " + itemId));

        // 3. Security Check: Ensure the item belongs to the logged-in dealer
        if (!existingItem.getDealerId().equals(currentDealerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to edit this item.");
        }

        // 4. Selectively update provided fields
        if (request.getCategoryId() != null) existingItem.setCategoryId(request.getCategoryId());
        if (request.getCategoryType() != null) existingItem.setCategoryType(request.getCategoryType().name());
        if (request.getTitle() != null) existingItem.setTitle(request.getTitle());
        if (request.getSellingPrice() != null) existingItem.setSellingPrice(request.getSellingPrice());
        if (request.getCostPrice() != null) existingItem.setCostPrice(request.getCostPrice());
        if (request.getStockQuantity() != null) existingItem.setStockQuantity(request.getStockQuantity());
        if (request.getCondition() != null) existingItem.setCondition(request.getCondition());
        if (request.getImages() != null) existingItem.setImages(request.getImages());
        if (request.getStatus() != null) existingItem.setStatus(request.getStatus().name());

        // Update dynamic attributes (merges or replaces options)
        if (request.getAttributes() != null && !request.getAttributes().isEmpty()) {
            if (existingItem.getAttributes() == null) {
                existingItem.setAttributes(request.getAttributes());
            } else {
                // Merge new attributes into existing attributes map
                existingItem.getAttributes().putAll(request.getAttributes());
            }
        }

        // 5. Save updated item
        Item updatedItem = itemRepository.save(existingItem);

        // 6. Build response
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Item updated successfully!");
        response.put("data", updatedItem);

        return ResponseEntity.ok(response);
    }
}