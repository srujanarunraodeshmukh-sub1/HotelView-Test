package com.raghunath.hotelview.service.admin;

import com.raghunath.hotelview.dto.admin.*;
import com.raghunath.hotelview.entity.Admin;
import com.raghunath.hotelview.entity.CompletedOrder;
import com.raghunath.hotelview.entity.CustomerDetails;
import com.raghunath.hotelview.entity.KitchenOrder;
import com.raghunath.hotelview.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompletedOrderService {

    private final CompleteOrderRepository completeOrderRepository;
    private final AdminRepository adminRepository;
    private final KitchenOrderRepository kitchenOrderRepository;
    private final TableRepository tableRepository;
    private final VersionService versionService;
    private final MenuItemRepository menuItemRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    // API 1: Paged Fetch
    public Page<DeliverySummaryDTO> getCompletedOrdersPaged(String hotelId, int pageNumber) {

        // Sort by checkoutDate and checkoutTime since checkoutAt field doesn't exist
        Pageable pageable = PageRequest.of(pageNumber, 10,
                Sort.by("checkoutDate").descending().and(Sort.by("checkoutTime").descending()));

        // 1. Fetch the Entity Page from MongoDB
        Page<CompletedOrder> entities = completeOrderRepository.findByHotelId(hotelId, pageable);

        // 2. Map Entities to DeliverySummaryDTO cleanly
        return entities.map(order -> {
            LocalDateTime dateTimeFallback = LocalDateTime.now();

            if (order.getCheckoutDate() != null && order.getCheckoutTime() != null) {
                try {
                    dateTimeFallback = LocalDateTime.parse(order.getCheckoutDate() + "T" + order.getCheckoutTime());
                } catch (Exception ignored) {
                    // Fallback to current time safely if date string parsing glitches
                }
            }

            return DeliverySummaryDTO.builder()
                    .id(order.getId())
                    .orderType(order.getOrderType())
                    .customerName(order.getCustomerName())
                    .customerMobile(order.getCustomerMobile())
                    .totalPayable(order.getTotalPayable())
                    .checkoutAt(dateTimeFallback)
                    .build();
        });
    }

    public Page<DeliverySummaryDTO> getCompletedOrdersByStaffPaged(String hotelId, String checkoutBy, int pageNumber) {
        // Sort by checkoutDate and checkoutTime descending chronologically
        Pageable pageable = PageRequest.of(pageNumber, 10,
                Sort.by("checkoutDate").descending().and(Sort.by("checkoutTime").descending()));

        // 1. Fetch the lightweight projected document page from MongoDB filtered by checkoutBy
        Page<CompletedOrder> entities = completeOrderRepository.findByHotelIdAndCheckoutBy(hotelId, checkoutBy, pageable);

        // 2. Map Entities to DeliverySummaryDTO cleanly
        return entities.map(order -> {
            LocalDateTime dateTimeFallback = LocalDateTime.now();

            if (order.getCheckoutDate() != null && order.getCheckoutTime() != null) {
                try {
                    dateTimeFallback = LocalDateTime.parse(order.getCheckoutDate() + "T" + order.getCheckoutTime());
                } catch (Exception ignored) {
                    // Fallback safely if string parsing glitches
                }
            }

            return DeliverySummaryDTO.builder()
                    .id(order.getId())
                    .orderType(order.getOrderType())
                    .customerName(order.getCustomerName())
                    .customerMobile(order.getCustomerMobile())
                    .totalPayable(order.getTotalPayable())
                    .checkoutAt(dateTimeFallback)
                    .build();
        });
    }

    public ReceiptResponse getReceiptDetails(String orderId, String hotelIdFromToken) {
        // 1. Fetch the Completed Order
        CompletedOrder order = completeOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.getHotelId().equals(hotelIdFromToken)) {
            throw new RuntimeException("Unauthorized access to this order");
        }

        // 2. Fetch Restaurant Profile
        Admin admin = adminRepository.findByHotelId(hotelIdFromToken)
                .orElseThrow(() -> new RuntimeException("Restaurant profile not found"));

        // 3. Extract items and units straight out of the document in memory
        List<ReceiptResponse.FlattenedItem> flattenedItems = new ArrayList<>();
        if (order.getAllOrders() != null) {
            for (KitchenOrder kitchenOrder : order.getAllOrders()) {
                if (kitchenOrder == null || kitchenOrder.getItems() == null) continue;

                for (OrderItem item : kitchenOrder.getItems()) {
                    // Clean trailing noise out of name strings safely inline
                    String rawName = item.getItemName() != null ? item.getItemName().trim() : "Unknown Item";
                    rawName = rawName.contains("//") ? rawName.split("//")[0].trim() : rawName;

                    // Clean trailing noise out of quantity strings safely inline
                    String rawQuantity = item.getQuantity() != null ? item.getQuantity().trim() : "1";
                    rawQuantity = rawQuantity.contains("//") ? rawQuantity.split("//")[0].trim() : rawQuantity;

                    // Directly read the stored unit from the CompletedOrder document snapshot
                    String unit = item.getUnit() != null ? item.getUnit().trim() : "PIECE";
                    unit = unit.contains("//") ? unit.split("//")[0].trim() : unit;
                    if (unit.isEmpty()) {
                        unit = "PIECE";
                    }

                    // 🌟 Populate item name and unit as completely independent properties
                    flattenedItems.add(ReceiptResponse.FlattenedItem.builder()
                            .itemName(rawName)      // Cleaned standalone item name (e.g. "Buttermilk")
                            .unit(unit)             // Cleaned standalone unit (e.g. "LITER")
                            .quantity(rawQuantity)
                            .price(item.getPrice())
                            .subTotal(item.getSubTotal())
                            .build());
                }
            }
        }

        // 4. Return response payload matching your exact schema layout
        return ReceiptResponse.builder()
                .restaurantName(admin.getRestaurantName())
                .restaurantAddress(admin.getRestaurantAddress())
                .restaurantContact(admin.getRestaurantContact())
                .restaurantLogo(admin.getRestaurantLogo())
                .restaurantUpi(admin.getRestaurantUpi())
                .orderId(order.getId())
                .date(order.getCheckoutDate())
                .time(order.getCheckoutTime())
                .orderType(order.getOrderType())
                .items(flattenedItems)
                .grandTotal(order.getGrandTotal())
                .discountPercent(order.getDiscountPercent())
                .discountAmount(order.getDiscountAmount())
                .totalPayable(order.getTotalPayable())
                .customerName(order.getCustomerName())
                .customerMobile(order.getCustomerMobile())
                .customerAddress(order.getCustomerAddress())
                .build();
    }

    public List<CompletedOrder> searchCompletedOrders(String hotelId, String query) {
        return completeOrderRepository.searchOrders(hotelId, query);
    }

    /**
     * FETCH TODAY'S COMPLETED HOME DELIVERIES (Clean Summary)
     */
    public List<DeliverySummaryDTO> getTodayCompletedHomeDeliveries(String hotelId) {
        ZonedDateTime nowIST = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        String todayDate = nowIST.format(DateTimeFormatter.ISO_LOCAL_DATE);

        List<String> externalTypes = List.of("HOME", "PARCEL");

        // ✅ Fix: Update repository method mapping chain to use checkoutTime sorting
        List<CompletedOrder> orders = completeOrderRepository
                .findByHotelIdAndOrderTypeInAndCheckoutDateOrderByCheckoutDateDescCheckoutTimeDesc(
                        hotelId, externalTypes, todayDate);

        return orders.stream().map(order -> {
            // ✅ Fix: Synthesize LocalDateTime from date/time string maps safely
            LocalDateTime dateTimeFallback = LocalDateTime.now();
            if (order.getCheckoutDate() != null && order.getCheckoutTime() != null) {
                try {
                    dateTimeFallback = LocalDateTime.parse(order.getCheckoutDate() + "T" + order.getCheckoutTime());
                } catch (Exception ignored) {}
            }

            return DeliverySummaryDTO.builder()
                    .id(order.getId())
                    .orderType(order.getOrderType())
                    .customerName(order.getCustomerName())
                    .customerMobile(order.getCustomerMobile())
                    .totalPayable(order.getTotalPayable())
                    .checkoutAt(dateTimeFallback)
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * 12. DELETE COMPLETED ORDER: Completely wipes out a bill document from the database collections
     * and reverses the customer's aggregate stats to keep financial indicators synchronized.
     */

    @Caching(evict = {
            @CacheEvict(value = "dashboardStatsCache", key = "#hotelId"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-today'"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-week'"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-month'"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-year'")
    })
    @Transactional
    public void deleteCompletedOrderById(String hotelId, String orderId) {
        CompletedOrder order = completeOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Completed order not found with ID: " + orderId));

        if (!order.getHotelId().equals(hotelId)) {
            throw new RuntimeException("Unauthorized action. Resource isolation block mismatch.");
        }

        // Deduct history values from Customer profiles
        if (StringUtils.hasText(order.getCustomerMobile()) && !"0000000000".equals(order.getCustomerMobile())) {
            Query query = new Query(Criteria.where("hotelId").is(hotelId).and("customerMobile").is(order.getCustomerMobile()));
            Update update = new Update()
                    .inc("totalOrders", -1)
                    .inc("totalAmountPaid", -order.getTotalPayable());

            mongoTemplate.updateFirst(query, update, CustomerDetails.class);
        }

        // Hard drop from MongoDB collection
        completeOrderRepository.delete(order);
        log.info("DELETE_COMPLETED: Permanently removed finalized order document record ID: {}", orderId);
    }

    @CacheEvict(value = "dashboardStatsCache", key = "#hotelId")
    @Transactional
    public void softDeleteOrders(String hotelId, List<String> orderIds) {
        // 1. Fetch orders from both sources
        List<CompletedOrder> completedOrders = completeOrderRepository.findAllById(orderIds)
                .stream().filter(o -> o.getHotelId().equals(hotelId)).toList();

        List<KitchenOrder> kitchenOrders = kitchenOrderRepository.findAllById(orderIds)
                .stream().filter(o -> o.getHotelId().equals(hotelId)).toList();

        if (completedOrders.isEmpty() && kitchenOrders.isEmpty()) {
            throw new RuntimeException("Unauthorized or Orders not found");
        }

        // ✅ FIX: Map isolated deductions specifically per table to prevent multi-table over-deletion balance wipes!
        Map<String, Double> tableDeductions = new HashMap<>();

        // Process Completed Orders
        for (CompletedOrder o : completedOrders) {
            double amt = (o.getTotalPayable() != null) ? o.getTotalPayable() : 0.0;
            if (o.getAllOrders() != null && !o.getAllOrders().isEmpty()) {
                String tName = o.getAllOrders().get(0).getTableName();
                if (tName != null) {
                    tableDeductions.put(tName, tableDeductions.getOrDefault(tName, 0.0) + amt);
                }
            }
        }

        // Process Kitchen Orders
        for (KitchenOrder o : kitchenOrders) {
            double amt = (o.getTotalAmount() != null) ? o.getTotalAmount() : 0.0;
            if (o.getTableName() != null) {
                tableDeductions.put(o.getTableName(), tableDeductions.getOrDefault(o.getTableName(), 0.0) + amt);
            }
        }

        // 3. Update the RestaurantTable currentBill accurately using your verified string tableName mapping
        tableDeductions.forEach((tableName, deductionForThisTable) -> {
            tableRepository.findByHotelIdAndTableName(hotelId, tableName).ifPresent(table -> {
                double current = (table.getCurrentBill() != null) ? table.getCurrentBill() : 0.0;
                double newBill = Math.max(0, current - deductionForThisTable);

                table.setCurrentBill(newBill);

                // If bill becomes 0, set status to INACTIVE safely
                if (newBill <= 0) {
                    table.setStatus("INACTIVE");
                }

                tableRepository.save(table);
            });
        });

        // 4. Move to Trash with deletedAt IST
        ZonedDateTime nowIST = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        String deletedAt = nowIST.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        List<org.bson.Document> trashDocs = new ArrayList<>();

        completedOrders.forEach(order -> {
            org.bson.Document doc = new org.bson.Document();
            mongoTemplate.getConverter().write(order, doc);
            doc.put("deletedAt", deletedAt);
            trashDocs.add(doc);
        });

        kitchenOrders.forEach(order -> {
            org.bson.Document doc = new org.bson.Document();
            mongoTemplate.getConverter().write(order, doc);
            doc.put("deletedAt", deletedAt);
            trashDocs.add(doc);
        });

        // 5. Final DB Operations
        if (!trashDocs.isEmpty()) {
            mongoTemplate.insert(trashDocs, "deleted_orders");
            if (!completedOrders.isEmpty()) completeOrderRepository.deleteAll(completedOrders);
            if (!kitchenOrders.isEmpty()) kitchenOrderRepository.deleteAll(kitchenOrders);
        }

        // 6. Sync Versions
        versionService.bumpSales(hotelId);
        versionService.bumpTables(hotelId);
    }

    public List<org.bson.Document> getDeletedOrders(String hotelId) {
        Query query = new Query(Criteria.where("hotelId").is(hotelId));
        List<org.bson.Document> rawDocs = mongoTemplate.find(query, org.bson.Document.class, "deleted_orders");

        return rawDocs.stream().map(doc -> {
            if (doc.containsKey("_id")) {
                doc.put("id", doc.get("_id").toString());
                doc.remove("_id");
            }
            doc.remove("_class");
            return doc;
        }).collect(Collectors.toList());
    }

    /**
     * 11. EDIT COMPLETED ORDER (Strict Overrides, 7-Day Limit & Inventory Stock Sync)
     * Overwrites quantities directly across all multi-block sub-orders, saves historical billing totals,
     * adjusts inventory dynamically depending on item unit calculations,
     * and strictly restricts modifications to within 7 days of the checkout date.
     */
    /**
     * 11. EDIT COMPLETED ORDER (Strict Overrides, 7-Day Limit, Inventory Stock Sync & Dynamic Financial Mapping)
     * Overwrites quantities directly across all multi-block sub-orders, applies frontend financial values directly,
     * recalculates inner kitchen order totals dynamically, and syncs stock adjustments safely.
     */
    /**
     * 11. EDIT COMPLETED ORDER (Strict Overrides, 7-Day Limit, Inventory Stock Sync & Dynamic Financial Mapping)
     * Overwrites quantities directly across all multi-block sub-orders, applies frontend financial values directly,
     * recalculates inner kitchen order totals dynamically, and syncs stock adjustments safely.
     */
    @Caching(evict = {
            @CacheEvict(value = "dashboardStatsCache", key = "#hotelId"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-today'"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-week'"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-month'"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-year'")
    })
    @Transactional
    public void editCompletedOrderDetails(String hotelId, String orderId, CompletedOrderEditDTO editDto) {
        System.out.println("[DEBUG START] Hitting editCompletedOrderDetails for orderId: " + orderId);

        // 1. Fetch original order from DB
        CompletedOrder order = completeOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Completed order record not found"));

        System.out.println("[DEBUG] Order fetched successfully. Hotel ID from DB: " + order.getHotelId() + " | Provided Hotel ID: " + hotelId);

        if (!order.getHotelId().equals(hotelId)) {
            throw new RuntimeException("Unauthorized action on this resource workspace");
        }

        if (order.getCheckoutDate() != null) {
            try {
                java.time.LocalDate checkoutDate = java.time.LocalDate.parse(order.getCheckoutDate());
                java.time.LocalDate todayIST = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
                System.out.println("[DEBUG] Timeline validation -> Checkout Date: " + checkoutDate + " | Today: " + todayIST);

                if (checkoutDate.plusDays(7).isBefore(todayIST)) {
                    throw new RuntimeException("Modification denied: This completed order is older than 7 days.");
                }
            } catch (java.time.format.DateTimeParseException e) {
                log.error("DATE_PARSE_ERROR: Failed to validate checkout timeline for order {}", orderId);
            }
        }

        if (order.getAllOrders() == null || order.getAllOrders().isEmpty()) {
            throw new RuntimeException("This completed order contains no inner order structural array elements");
        }

        // Print all items that actually exist inside this document before filtering
        System.out.println("[DEBUG] --- Listing ALL items currently inside the database document ---");
        order.getAllOrders().stream()
                .filter(ko -> ko != null && ko.getItems() != null)
                .flatMap(ko -> ko.getItems().stream())
                .forEach(item -> System.out.println(" -> Found DB Item Name: '" + item.getItemName() + "' [Length: " + (item.getItemName() != null ? item.getItemName().length() : 0) + "]"));
        System.out.println("[DEBUG] ---------------------------------------------------------------");

        // 3. MAP NEW QUANTITIES, SUBTOTALS, AND DYNAMICALLY ADJUST INVENTORY
        if (editDto.getItems() != null && !editDto.getItems().isEmpty()) {
            for (CompletedOrderEditDTO.UpdatedItemPayload incomingItem : editDto.getItems()) {

                System.out.println("[DEBUG] Processing incoming item payload element -> Name: '" + incomingItem.getItemName() + "'");

                // Sanitize incoming item name inline
                String incomingNameClean = incomingItem.getItemName() != null ? incomingItem.getItemName().trim() : "";
                incomingNameClean = incomingNameClean.contains("//") ? incomingNameClean.split("//")[0].trim() : incomingNameClean;
                String finalIncomingName = incomingNameClean;

                System.out.println("[DEBUG] Sanitized Payload Item Name to match: '" + finalIncomingName + "' [Length: " + finalIncomingName.length() + "]");

                // Scan across every single sub-order array element to locate the live reference object
                OrderItem originalDbItem = order.getAllOrders().stream()
                        .filter(ko -> ko != null && ko.getItems() != null)
                        .flatMap(ko -> ko.getItems().stream())
                        .filter(item -> {
                            if (item == null || item.getItemName() == null) return false;

                            // Strip out hidden newlines (\r, \n), spaces, and convert to lowercase
                            String cleanDbName = item.getItemName().replaceAll("[\\r\\n]", "").trim().toLowerCase();
                            String cleanPayloadName = finalIncomingName.replaceAll("[\\r\\n]", "").trim().toLowerCase();

                            boolean matchResult = cleanDbName.equals(cleanPayloadName);
                            System.out.println("   [FILTER COMPARE] DB Name: '" + cleanDbName + "' vs Payload Name: '" + cleanPayloadName + "' -> Match Status: " + matchResult);

                            return matchResult;
                        })
                        .findFirst()
                        .orElseThrow(() -> {
                            System.out.println("[CRITICAL ERROR] Matching failed! Throwing item not found exception.");
                            return new RuntimeException("Item '" + finalIncomingName + "' does not exist in the original bill. Only modifications are allowed.");
                        });

                System.out.println("[DEBUG] Successfully matched item inside stream! Proceeding to parse metrics for: " + originalDbItem.getItemName());

                if (incomingItem.getQuantity() != null) {
                    String cleanOldQtyStr = originalDbItem.getQuantity() != null ? originalDbItem.getQuantity().trim().toLowerCase() : "1";
                    cleanOldQtyStr = cleanOldQtyStr.contains("//") ? cleanOldQtyStr.split("//")[0].trim() : cleanOldQtyStr;

                    String cleanNewQtyStr = incomingItem.getQuantity().trim().toLowerCase();
                    cleanNewQtyStr = cleanNewQtyStr.contains("//") ? cleanNewQtyStr.split("//")[0].trim() : cleanNewQtyStr;

                    double oldNumericValue = 1.0;
                    double newNumericValue = 1.0;

                    try {
                        String oldNumericPart = cleanOldQtyStr.replaceAll("[^0-9.]", "");
                        if (org.springframework.util.StringUtils.hasText(oldNumericPart)) {
                            oldNumericValue = Double.parseDouble(oldNumericPart);
                        }

                        String newNumericPart = cleanNewQtyStr.replaceAll("[^0-9.]", "");
                        if (org.springframework.util.StringUtils.hasText(newNumericPart)) {
                            newNumericValue = Double.parseDouble(newNumericPart);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Quantity for '" + originalDbItem.getItemName() + "' must contain valid numeric configurations.");
                    }

                    if (newNumericValue < 0) {
                        throw new RuntimeException("Quantity for '" + originalDbItem.getItemName() + "' cannot be negative.");
                    }

                    // --- INVENTORY STOCK SYNC LOGIC ---
                    String itemUnit = originalDbItem.getUnit() != null ? originalDbItem.getUnit().toUpperCase() : "PIECE";

                    double oldCalculatedDeduction = oldNumericValue;
                    double newCalculatedDeduction = newNumericValue;

                    if (cleanOldQtyStr.contains("ml") && "LITER".equals(itemUnit)) {
                        oldCalculatedDeduction = oldNumericValue / 1000.0;
                    } else if (cleanOldQtyStr.contains("gm") && "KG".equals(itemUnit)) {
                        oldCalculatedDeduction = oldNumericValue / 1000.0;
                    }

                    if (cleanNewQtyStr.contains("ml") && "LITER".equals(itemUnit)) {
                        newCalculatedDeduction = newNumericValue / 1000.0;
                    } else if (cleanNewQtyStr.contains("gm") && "KG".equals(itemUnit)) {
                        newCalculatedDeduction = newNumericValue / 1000.0;
                    }

                    double inventoryStockAdjustmentDelta = newCalculatedDeduction - oldCalculatedDeduction;

                    if (inventoryStockAdjustmentDelta != 0.0) {
                        try {
                            Query stockQuery = new Query(Criteria.where("hotelId").is(hotelId)
                                    .and("name").regex("^" + java.util.regex.Pattern.quote(originalDbItem.getItemName()) + "$", "i"));

                            Update stockUpdate = new Update().inc("totalQuantity", -inventoryStockAdjustmentDelta);
                            mongoTemplate.updateFirst(stockQuery, stockUpdate, com.raghunath.hotelview.entity.MenuItem.class);
                            System.out.println("[DEBUG] Stock adjusted for '" + originalDbItem.getItemName() + "' | Shift Balance: " + (-inventoryStockAdjustmentDelta));
                        } catch (Exception e) {
                            log.error("INVENTORY_EDIT_CRITICAL_ERR: Failed stock adjustment processing for item '{}'", originalDbItem.getItemName(), e);
                        }
                    }

                    originalDbItem.setQuantity(cleanNewQtyStr);
                }

                if (incomingItem.getSubTotal() != null) {
                    originalDbItem.setSubTotal(incomingItem.getSubTotal());
                }
            }
        }

        // 🌟 4. RECALCULATE KITCHEN ORDER TOTAL AMOUNT METRICS FOR EACH INNER BLOCK (Subtotal to totalAmount logic)
        for (KitchenOrder subOrder : order.getAllOrders()) {
            if (subOrder != null && subOrder.getItems() != null) {
                double computedBlockTotal = subOrder.getItems().stream()
                        .filter(java.util.Objects::nonNull)
                        .mapToDouble(OrderItem::getSubTotal)
                        .sum();
                subOrder.setTotalAmount(computedBlockTotal);
                System.out.println("[DEBUG] Synced KitchenOrder block ID: " + subOrder.getId() + " totalAmount updated to: " + computedBlockTotal);
            }
        }

        // 🌟 5. APPLY TOP-LEVEL FINANCIAL OVERRIDES FROM PAYLOAD
        if (editDto.getGrandTotal() != null) {
            order.setGrandTotal(editDto.getGrandTotal());
        }
        if (editDto.getDiscountAmount() != null) {
            order.setDiscountAmount(editDto.getDiscountAmount());
        }
        if (editDto.getDiscountPercent() != null) {
            order.setDiscountPercent(editDto.getDiscountPercent());
        }

        if (editDto.getCustomTotalPayable() == null) {
            throw new RuntimeException("Total payable field value must be explicitly provided for this update operation.");
        }

        double oldTotalPayable = order.getTotalPayable() != null ? order.getTotalPayable() : 0.0;
        double finalTotalPayable = Math.max(0.0, editDto.getCustomTotalPayable());

        order.setLastTotalPayable(oldTotalPayable);
        order.setTotalPayable(finalTotalPayable);

        // Save completely transformed data structure back to MongoDB
        completeOrderRepository.save(order);
        System.out.println("[DEBUG] CompletedOrder record saved to MongoDB database successfully.");

        // 6. Adjust Customer metrics balance
        double financialDelta = finalTotalPayable - oldTotalPayable;
        if (org.springframework.util.StringUtils.hasText(order.getCustomerMobile()) && !"0000000000".equals(order.getCustomerMobile())) {
            Query query = new Query(Criteria.where("hotelId").is(hotelId).and("customerMobile").is(order.getCustomerMobile()));
            Update update = new Update().inc("totalAmountPaid", financialDelta);
            mongoTemplate.updateFirst(query, update, CustomerDetails.class);
        }

        System.out.println("[DEBUG END] Processing finalized successfully without errors.");
    }

    public List<InventoryItemsResponse> getOrderInventoryDetails(String hotelId, String orderId) {
        // 1. Fetch the master bill tracking instance
        CompletedOrder order = completeOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Completed order history record not found"));

        // Tenant protection gate
        if (!order.getHotelId().equals(hotelId)) {
            throw new RuntimeException("Unauthorized resource isolation space query rejected");
        }

        if (order.getAllOrders() == null || order.getAllOrders().isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Extract all items from all order blocks
        List<OrderItem> allOrderedItems = order.getAllOrders().stream()
                .filter(subOrderBlock -> subOrderBlock != null && subOrderBlock.getItems() != null)
                .flatMap(subOrderBlock -> subOrderBlock.getItems().stream())
                .collect(Collectors.toList());

        if (allOrderedItems.isEmpty()) {
            return Collections.emptyList();
        }

        List<InventoryItemsResponse> targetDetailsList = new ArrayList<>();

        // 3. Loop through all items
        for (OrderItem item : allOrderedItems) {
            boolean foundInMenu = false;

            // CASE A: Has valid itemId and not EXTERNAL — fetch from menu_items by explicit ID
            if (item.getItemId() != null
                    && !item.getItemId().trim().isEmpty()
                    && !item.getItemId().equals("EXTERNAL")) {

                Query menuQuery = new Query(Criteria.where("_id").is(item.getItemId()));
                com.raghunath.hotelview.entity.MenuItem menuItem =
                        mongoTemplate.findOne(menuQuery, com.raghunath.hotelview.entity.MenuItem.class, "menu_items");

                if (menuItem != null) {
                    targetDetailsList.add(InventoryItemsResponse.builder()
                            .name(menuItem.getName())
                            .price(String.valueOf(menuItem.getPrice()))
                            .quantity(menuItem.getQuantity())
                            .unit(menuItem.getUnit() != null ? menuItem.getUnit() : menuItem.getTotalquantityUnit())
                            .isPresent(true)
                            .build());
                    foundInMenu = true;
                }
            }

            // 🌟 FALLBACK CASE: If lookup by ID skipped or returned null, fall back to case-insensitive name mapping
            if (!foundInMenu && item.getItemName() != null) {
                String sanitizedName = item.getItemName().trim();
                Query nameQuery = new Query(Criteria.where("hotelId").is(hotelId)
                        .and("name").regex("^" + java.util.regex.Pattern.quote(sanitizedName) + "$", "i"));

                com.raghunath.hotelview.entity.MenuItem menuItem =
                        mongoTemplate.findOne(nameQuery, com.raghunath.hotelview.entity.MenuItem.class, "menu_items");

                if (menuItem != null) {
                    targetDetailsList.add(InventoryItemsResponse.builder()
                            .name(menuItem.getName())
                            .price(String.valueOf(menuItem.getPrice()))
                            .quantity(menuItem.getQuantity())
                            .unit(menuItem.getUnit() != null ? menuItem.getUnit() : menuItem.getTotalquantityUnit())
                            .isPresent(true)
                            .build());
                    foundInMenu = true;
                }
            }

            // CASE B: Not found in menu catalog by ID or Name — return fallback placeholder state
            if (!foundInMenu) {
                targetDetailsList.add(InventoryItemsResponse.builder()
                        .name(item.getItemName() != null ? item.getItemName() : "Unknown Item")
                        .isPresent(false)
                        .build());
            }
        }

        return targetDetailsList;
    }
}