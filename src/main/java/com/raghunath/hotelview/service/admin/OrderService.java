package com.raghunath.hotelview.service.admin;

import com.raghunath.hotelview.dto.admin.*;
import com.raghunath.hotelview.entity.*;
import com.raghunath.hotelview.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.util.StringUtils;
import com.mongodb.client.result.UpdateResult;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderDraftRepository draftRepository;
    private final AdminRepository adminRepository;
    private final TableRepository tableRepository;
    private final EmployeeRepository employeeRepository;
    private final MenuItemRepository menuItemRepository;
    private final KitchenOrderRepository kitchenOrderRepository;
    private final MongoTemplate mongoTemplate;
    private final CompleteOrderRepository completeOrderRepository;
    private final VersionService versionService;
    private final ExternalOrderRepository externalOrderRepository;
    private final SalesAggregationRepository salesAggregationRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    /**
     * 1. SAVE DRAFT: Waiter/Admin adds items.
     */
    public void saveDraft(String hotelId, String tableName, List<OrderItem> items) {
        validateTableExists(hotelId, tableName);
        Double total = items.stream().mapToDouble(OrderItem::getSubTotal).sum();

        OrderDraft draft = draftRepository.findByHotelIdAndTableName(hotelId, tableName)
                .orElse(OrderDraft.builder()
                        .hotelId(hotelId)
                        .tableName(tableName)
                        .build());

        draft.setItems(items);
        draft.setTotalAmount(total);
        draft.setUpdatedAt(LocalDateTime.now());
        draftRepository.save(draft);
    }

    /**
     * 2. FETCH DRAFT: View current unsent items.
     */
    public OrderDraft getDraft(String hotelId, String tableName) {
        return draftRepository.findByHotelIdAndTableName(hotelId, tableName).orElse(null);
    }

    /**
     * 3. CONFIRM ORDER: Moves items to the Kitchen.
     */
    /**
     * 3. CONFIRM ORDER: Moves items to the Kitchen.
     */
    @CacheEvict(value = "dashboardStatsCache", key = "#hotelId")
    @Transactional
    public String confirmOrder(String hotelId, String tableName, List<OrderItem> items, String waiterId, String comment) {
        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (getISTNow().toLocalDateTime().isAfter(admin.getSubscriptionExpiry())) {
            throw new RuntimeException("Kindly upgrade to the Standard or Premium plan.");
        }

        validateTableExists(hotelId, tableName);
        ZonedDateTime nowIST = getISTNow();
        Double total = items.stream().mapToDouble(OrderItem::getSubTotal).sum();

        if (items != null) {
            items.forEach(item -> {
                // 🌟 FIXED HERE: Explicitly capture and sanitize the itemId passed by the frontend request payload
                if (item.getItemId() != null) {
                    item.setItemId(item.getItemId().contains("//") ? item.getItemId().split("//")[0].trim() : item.getItemId().trim());
                }

                // Clean itemName trailing slashes manually inline
                if (item.getItemName() != null) {
                    item.setItemName(item.getItemName().contains("//") ? item.getItemName().split("//")[0].trim() : item.getItemName().trim());
                }

                // Clean quantity trailing slashes manually inline
                if (item.getQuantity() != null) {
                    item.setQuantity(item.getQuantity().contains("//") ? item.getQuantity().split("//")[0].trim() : item.getQuantity().trim());
                }

                // Clean and set unit passed by the frontend directly inline
                if (item.getUnit() == null || item.getUnit().trim().isEmpty()) {
                    item.setUnit("PIECE");
                } else {
                    item.setUnit(item.getUnit().contains("//") ? item.getUnit().split("//")[0].trim() : item.getUnit().trim());
                }
            });
        }

        KitchenOrder kOrder = KitchenOrder.builder()
                .hotelId(hotelId)
                .tableName(tableName)
                .orderType("TABLE")
                .items(items)
                .totalAmount(total)
                .status("PENDING")
                .comments(comment)
                .placedBy(waiterId)
                .createdDate(nowIST.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .createdTime(nowIST.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .build();

        kitchenOrderRepository.save(kOrder);
        draftRepository.findByHotelIdAndTableName(hotelId, tableName).ifPresent(draftRepository::delete);

        updateTableVisualStatus(hotelId, tableName, "PENDING");
        updateTableBill(hotelId, tableName, total, false);

        return "Order sent to kitchen";
    }

    @CacheEvict(value = "dashboardStatsCache", key = "#hotelId")
    @Transactional
    public String confirmCustomItemOrder(String hotelId, CustomKitchenOrderRequest request, String waiterId) {
        // 1. Subscription Check (Mirrors original logic perfectly)
        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (getISTNow().toLocalDateTime().isAfter(admin.getSubscriptionExpiry())) {
            throw new RuntimeException("Kindly upgrade to the Standard or Premium plan.");
        }

        // 2. Fallback Defaults & Dynamic Validation
        String tableName = org.springframework.util.StringUtils.hasText(request.getTableName()) ? request.getTableName() : "Counter";
        String orderType = org.springframework.util.StringUtils.hasText(request.getOrderType()) ? request.getOrderType() : "TABLE";

        if (orderType.equals("TABLE")) {
            validateTableExists(hotelId, tableName);
        }

        if (!org.springframework.util.StringUtils.hasText(request.getItemName())) {
            throw new RuntimeException("Custom item name cannot be empty");
        }

        Double total = (request.getSubTotal() != null) ? request.getSubTotal() : 0.0;
        ZonedDateTime nowIST = getISTNow();

        // 3. Build the OrderItem DTO dynamically on-the-fly
        // Note: Converted quantity integer to String to prevent type casting bugs on your schema!
        OrderItem customItem = OrderItem.builder()
                .itemName(request.getItemName())
                .quantity(request.getQuantity() != null ? String.valueOf(request.getQuantity()) : "1")
                .price(request.getQuantity() != null && request.getQuantity() > 0 ? total / request.getQuantity() : total)
                .subTotal(total)
                .build();

        List<OrderItem> itemsList = new ArrayList<>();
        itemsList.add(customItem);

        // 4. Construct KitchenOrder entity matching image_c49357.png
        KitchenOrder kOrder = KitchenOrder.builder()
                .hotelId(hotelId)
                .tableName(tableName)
                .orderType(orderType)
                .items(itemsList)
                .totalAmount(total)
                .status("PENDING")
                .comments(request.getComment() != null ? request.getComment() : "")
                .placedBy(waiterId)
                .createdDate(nowIST.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .createdTime(nowIST.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .build();

        // 5. Database Save & Visual Sync Operations
        kitchenOrderRepository.save(kOrder);

        // Clear layout drafts if they exist for this section
        draftRepository.findByHotelIdAndTableName(hotelId, tableName).ifPresent(draftRepository::delete);

        // Update real-time metrics across your multi-tenant layouts
        if (orderType.equals("TABLE")) {
            updateTableVisualStatus(hotelId, tableName, "PENDING");
            updateTableBill(hotelId, tableName, total, false);
        }

        return "Custom item order sent to kitchen successfully";
    }

    private void validateTableExists(String hotelId, String tableName) {
        if (!tableRepository.existsByHotelIdAndTableName(hotelId, tableName)) {
            log.error("VALIDATION_FAILED: Table {} not found for Hotel {}", tableName, hotelId);
            throw new RuntimeException("Invalid Table Number: " + tableName);
        }
    }

    /**
     * 4. CONFIRM HOME DELIVERY ORDER: Moves items to the Kitchen.
     */
    /**
     * 4. CONFIRM HOME DELIVERY ORDER: Moves items to the Kitchen.
     */
    /**
     * 4. CONFIRM HOME DELIVERY ORDER: Moves items to the Kitchen.
     */
    @CacheEvict(value = "dashboardStatsCache", key = "#hotelId")
    @Transactional
    public String confirmHomeDelivery(String hotelId, List<OrderItem> items, String waiterId, String orderType) {
        // 1. Subscription Check
        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (getISTNow().toLocalDateTime().isAfter(admin.getSubscriptionExpiry())) {
            throw new RuntimeException("Kindly upgrade to the Standard or Premium plan.");
        }

        if (items != null) {
            items.forEach(item -> {
                // Clean itemName trailing slashes manually inline
                if (item.getItemName() != null) {
                    item.setItemName(item.getItemName().contains("//") ? item.getItemName().split("//")[0].trim() : item.getItemName().trim());
                }

                // Clean quantity trailing slashes manually inline
                if (item.getQuantity() != null) {
                    item.setQuantity(item.getQuantity().contains("//") ? item.getQuantity().split("//")[0].trim() : item.getQuantity().trim());
                }

                // 🌟 Directly capture and sanitize the unit passed by the frontend inline
                if (item.getUnit() == null || item.getUnit().trim().isEmpty()) {
                    item.setUnit("PIECE");
                } else {
                    item.setUnit(item.getUnit().contains("//") ? item.getUnit().split("//")[0].trim() : item.getUnit().trim());
                }
            });
        }

        // 🌟 FIXED: Removed reduceInventoryStock from here so it only deducts once at checkout!

        // 2. Original Logic
        ZonedDateTime nowIST = getISTNow();
        Double total = items.stream().mapToDouble(OrderItem::getSubTotal).sum();

        KitchenOrder deliveryOrder = KitchenOrder.builder()
                .hotelId(hotelId)
                .tableName(null)
                .orderType(orderType.toUpperCase())
                .items(items)
                .totalAmount(total)
                .status("PENDING")
                .placedBy(waiterId)
                .createdDate(nowIST.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .createdTime(nowIST.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .build();

        String savedId = kitchenOrderRepository.save(deliveryOrder).getId();
        versionService.bumpTables(hotelId);

        return savedId;
    }

    /**
     * 5. FETCH TABLE ORDERS: Latest orders first.
     * Fetches both Kitchen Orders AND pending Customer QR Orders for the table,
     * merging them into the exact same List<KitchenOrder> array structure.
     */
    public List<KitchenOrder> getOrdersByTable(String hotelId, String tableName) {
        validateTableExists(hotelId, tableName);

        // 1. Fetch active orders from kitchen_orders
        List<KitchenOrder> kitchenOrders = kitchenOrderRepository
                .findByHotelIdAndTableNameAndStatusNotOrderByCreatedDateDescCreatedTimeDesc(
                        hotelId, tableName, "PAID");

        // 2. Fetch pending QR orders from customer_table_orders
        List<CustomerOrder> customerOrders = customerOrderRepository
                .findByHotelIdAndTableNameAndStatusNotOrderByCreatedDateDescCreatedTimeDesc(
                        hotelId, tableName, "PAID");

        if (customerOrders == null || customerOrders.isEmpty()) {
            return kitchenOrders;
        }

        // 3. Convert pending CustomerOrders to KitchenOrder structure seamlessly
        List<KitchenOrder> pendingConverted = customerOrders.stream()
                .map(co -> KitchenOrder.builder()
                        .id(co.getId())
                        .hotelId(co.getHotelId())
                        .tableName(co.getTableName())
                        .orderType(co.getOrderType() != null ? co.getOrderType() : "TABLE")
                        .items(co.getItems())
                        .totalAmount(co.getTotalAmount())
                        .status(co.getStatus()) // "PENDING_ACCEPTANCE"
                        .comments(co.getComments())
                        .placedBy(co.getPlacedBy() != null ? co.getPlacedBy() : "CUSTOMER") // Indicates QR order awaiting approval
                        .acceptedBy(co.getEmployeeAcceptedBy())
                        .createdDate(co.getCreatedDate())
                        .createdTime(co.getCreatedTime())
                        .build())
                .toList();

        // 4. Merge both lists together
        List<KitchenOrder> combinedList = new ArrayList<>(pendingConverted);
        combinedList.addAll(kitchenOrders);

        // 5. Sort combined list by date & time descending (latest orders first)
        combinedList.sort((o1, o2) -> {
            String dt1 = (o1.getCreatedDate() != null ? o1.getCreatedDate() : "") + " " + (o1.getCreatedTime() != null ? o1.getCreatedTime() : "");
            String dt2 = (o2.getCreatedDate() != null ? o2.getCreatedDate() : "") + " " + (o2.getCreatedTime() != null ? o2.getCreatedTime() : "");
            return dt2.compareTo(dt1);
        });

        return combinedList;
    }

    /**
     * 6. CHECKOUT ORDERS: checkout all orders.
     */
    @Caching(evict = {
            @CacheEvict(value = "dashboardStatsCache", key = "#hotelId"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-today'"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-week'"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-month'"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-year'"),
            @CacheEvict(value = "menuSummaryCache", key = "#hotelId"),
            @CacheEvict(value = "menuCache", key = "#hotelId")
    })
    @Transactional
    public CheckoutResponse checkoutOrders(String hotelId, CheckoutRequest request, String checkoutBy) {
        // 1. Subscription Check
        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (getISTNow().toLocalDateTime().isAfter(admin.getSubscriptionExpiry())) {
            throw new RuntimeException("Your subscription plan has ended.");
        }

        // 2. Fetch Active Orders
        List<KitchenOrder> activeOrders = kitchenOrderRepository.findAllById(request.getOrderIds());
        if (activeOrders.isEmpty()) {
            throw new RuntimeException("No active orders found");
        }
        String tableName = activeOrders.get(0).getTableName();
        String parentOrderType = activeOrders.get(0).getOrderType() != null ? activeOrders.get(0).getOrderType() : "TABLE";

        // 🌟 Runs safely now since we optimized reduceInventoryStock to prefer item.getItemId()!
        for (KitchenOrder order : activeOrders) {
            reduceInventoryStock(hotelId, order.getItems());
        }

        // 3. Aggregate Items cleanly across multiple sub-orders
        Map<String, CheckoutResponse.BillItem> itemMap = new HashMap<>();
        for (KitchenOrder order : activeOrders) {
            if (order.getItems() == null) continue;

            order.getItems().forEach(item -> {
                if (item.getItemName() == null) return;

                String rawName = item.getItemName().trim();
                String normalizedKey = rawName.toLowerCase();

                // 🌟 FIX: Pull the saved unit directly out of the order item snapshot instead of querying by name
                String itemUnit = item.getUnit() != null ? item.getUnit().trim() : "PIECE";

                double directSubTotal = item.getSubTotal() != null ? item.getSubTotal() : 0.0;
                double baseUnitPrice = item.getPrice() != null ? item.getPrice() : 0.0;

                CheckoutResponse.BillItem initialVal = CheckoutResponse.BillItem.builder()
                        .itemName(rawName)
                        .quantity(item.getQuantity() != null ? item.getQuantity().trim() : "1")
                        .price(item.getPrice() != null ? item.getPrice() : (item.getSubTotal() != null ? item.getSubTotal() : 0.0))
                        .unit(itemUnit)
                        .subTotal(directSubTotal)
                        .build();

                itemMap.merge(normalizedKey, initialVal, (oldVal, newVal) -> {
                    double oldQty = 0.0;
                    double newQty = 0.0;
                    try {
                        String oldNumPart = oldVal.getQuantity().replaceAll("[^0-9.]", "");
                        String newNumPart = newVal.getQuantity().replaceAll("[^0-9.]", "");

                        oldQty = oldNumPart.isEmpty() ? 0.0 : Double.parseDouble(oldNumPart);
                        newQty = newNumPart.isEmpty() ? 0.0 : Double.parseDouble(newNumPart);
                    } catch (Exception e) {
                        log.error("QTY_MERGE_ERROR: Failed parsing quantities for item '{}'", item.getItemName());
                    }

                    double combinedQty = oldQty + newQty;
                    String metricSuffix = newVal.getQuantity().replaceAll("[0-9.]", "").trim();

                    if (!metricSuffix.isEmpty()) {
                        oldVal.setQuantity(combinedQty % 1 == 0 ? String.format("%.0f %s", combinedQty, metricSuffix) : String.format("%.2f %s", combinedQty, metricSuffix));
                    } else {
                        oldVal.setQuantity(combinedQty % 1 == 0 ? String.format("%.0f", combinedQty) : String.format("%.2f", combinedQty));
                    }

                    double oldSub = oldVal.getSubTotal() != null ? oldVal.getSubTotal() : 0.0;
                    double newSub = newVal.getSubTotal() != null ? newVal.getSubTotal() : 0.0;
                    oldVal.setSubTotal(oldSub + newSub);

                    return oldVal;
                });
            });
        }

        Double grandTotal = activeOrders.stream().mapToDouble(KitchenOrder::getTotalAmount).sum();
        Double discountPercent = (request.getDiscount() != null) ? request.getDiscount() : 0.0;
        Double discountAmount = (grandTotal * discountPercent) / 100;
        Double totalPayable = grandTotal - discountAmount;

        ZonedDateTime nowIST = getISTNow();

        List<KitchenOrderSnapshot> snapshots = activeOrders.stream().map(order ->
                KitchenOrderSnapshot.builder()
                        .id(order.getId())
                        .items(order.getItems())
                        .totalAmount(order.getTotalAmount())
                        .status(order.getStatus())
                        .comments(order.getComments())
                        .placedBy(order.getPlacedBy())
                        .createdDate(order.getCreatedDate())
                        .createdTime(order.getCreatedTime())
                        .build()
        ).collect(java.util.stream.Collectors.toList());

        // 4. Build and Save Completed Order
        CompletedOrder finalBill = CompletedOrder.builder()
                .hotelId(hotelId)
                .orderType(activeOrders.get(0).getOrderType())
                .tableName(tableName)
                .customerName(StringUtils.hasText(request.getCustomerName()) ? request.getCustomerName() : "Walk-in Guest")
                .customerMobile(StringUtils.hasText(request.getCustomerMobile()) ? request.getCustomerMobile() : "0000000000")
                .customerAddress(StringUtils.hasText(request.getCustomerAddress()) ? request.getCustomerAddress() : "N/A")
                .allOrders(snapshots)
                .grandTotal(grandTotal)
                .paymentStatus("PAID")
                .discountPercent(discountPercent)
                .discountAmount(discountAmount)
                .totalPayable(totalPayable)
                .checkoutBy(checkoutBy)
                .checkoutDate(nowIST.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .checkoutTime(nowIST.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .build();

        CompletedOrder savedBill = completeOrderRepository.save(finalBill);

        // 5. Cleanup and Sync
        if (savedBill.getId() != null) {
            syncCustomerDetails(hotelId, finalBill);
            kitchenOrderRepository.deleteAll(activeOrders);
            updateTableBill(hotelId, tableName, 0.0, true);
        }

        // 6. Build Response
        String fullId = savedBill.getId();
        String shortId = (fullId != null && fullId.length() > 6) ? fullId.substring(fullId.length() - 6) : fullId;

        List<OrderItem> allItems = activeOrders.stream()
                .filter(o -> o.getItems() != null)
                .flatMap(o -> o.getItems().stream())
                .toList();
        updateDailyAnalytics(hotelId, checkoutBy, totalPayable, allItems, true);

        return CheckoutResponse.builder()
                .id(fullId)
                .shortId(shortId)
                .checkoutDate(finalBill.getCheckoutDate())
                .checkoutTime(finalBill.getCheckoutTime())
                .orderType(finalBill.getOrderType())
                .tableName(finalBill.getTableName())
                .customerName(finalBill.getCustomerName())
                .customerMobile(finalBill.getCustomerMobile())
                .customerAddress(finalBill.getCustomerAddress())
                .items(new ArrayList<>(itemMap.values()))
                .grandTotal(grandTotal)
                .discountPercent(discountPercent)
                .discountAmount(discountAmount)
                .totalPayable(totalPayable)
                .restaurantName(admin.getRestaurantName())
                .restaurantAddress(admin.getRestaurantAddress())
                .restaurantContact(admin.getRestaurantContact())
                .restaurantUpi(admin.getRestaurantUpi())
                .restaurantLogo(admin.getRestaurantLogo())
                .build();
    }

    /**
     * 7. INSTANT CHECKOUT
     */
    @Caching(evict = {
            @CacheEvict(value = "dashboardStatsCache", key = "#hotelId"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-today'"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-week'"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-month'"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-year'"),
            @CacheEvict(value = "menuSummaryCache", key = "#hotelId"),
            @CacheEvict(value = "menuCache", key = "#hotelId")
    })
    @Transactional
    public CheckoutResponse instantOrderAndCheckout(String hotelId, InstantCheckoutRequest request, String actualLoggedInUser) {
        // 1. Subscription Check
        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (getISTNow().toLocalDateTime().isAfter(admin.getSubscriptionExpiry())) {
            throw new RuntimeException("Kindly upgrade to the Standard or Premium plan.");
        }

        List<OrderItem> items = request.getItems();
        if (items == null || items.isEmpty()) {
            throw new RuntimeException("Cannot process checkout with empty items.");
        }

        Double grandTotal = items.stream().mapToDouble(OrderItem::getSubTotal).sum();
        Double discountPercent = request.getDiscountPercent() != null ? request.getDiscountPercent() : 0.0;
        Double discountAmount = (grandTotal * discountPercent) / 100.0;
        Double totalPayable = Math.max(0.0, grandTotal - discountAmount);

        ZonedDateTime nowIST = getISTNow();

        if (items != null) {
            items.forEach(item -> {
                // Clean trailing slashes out of name and quantity strings inline
                if (item.getItemName() != null) {
                    item.setItemName(item.getItemName().contains("//") ? item.getItemName().split("//")[0].trim() : item.getItemName().trim());
                }
                if (item.getQuantity() != null) {
                    item.setQuantity(item.getQuantity().contains("//") ? item.getQuantity().split("//")[0].trim() : item.getQuantity().trim());
                }

                String menuUnit = "PIECE";

                // 🌟 Query by itemId instead of Name to ensure an absolute unique result and avoid duplicate record naming crashes
                if (item.getItemId() != null && !item.getItemId().trim().isEmpty()) {
                    Optional<?> menuItemOpt = menuItemRepository.findById(item.getItemId().trim());

                    if (menuItemOpt.isPresent()) {
                        Object rawItem = menuItemOpt.get();
                        if (rawItem instanceof com.raghunath.hotelview.entity.MenuItem dbItem) {
                            menuUnit = dbItem.getUnit() != null ? dbItem.getUnit() : dbItem.getTotalquantityUnit();
                        }
                    }
                } else {
                    // Fallback: If itemId isn't provided, use name but get the first result rather than crashing
                    List<com.raghunath.hotelview.entity.MenuItem> fallbackList = mongoTemplate.find(
                            new Query(Criteria.where("hotelId").is(hotelId)
                                    .and("name").regex("^" + java.util.regex.Pattern.quote(item.getItemName()) + "$", "i")),
                            com.raghunath.hotelview.entity.MenuItem.class,
                            "menu_items"
                    );
                    if (!fallbackList.isEmpty()) {
                        com.raghunath.hotelview.entity.MenuItem dbItem = fallbackList.get(0);
                        menuUnit = dbItem.getUnit() != null ? dbItem.getUnit() : dbItem.getTotalquantityUnit();
                    }
                }

                item.setUnit(menuUnit);
            });
        }

        // 🌟 2. DYNAMIC INVENTORY STOCK DEDUCTION (Now runs safely with pre-mapped unit snapshot configurations!)
        reduceInventoryStock(hotelId, items);

        // 3. IN-MEMORY TRANSIENT OBJECT
        KitchenOrderSnapshot transientOrderSnapshot = KitchenOrderSnapshot.builder()
                .items(items)
                .totalAmount(grandTotal)
                // Implicitly left out: hotelId, tableName, orderType do not exist in the snapshot class!
                .build();

        // 4. Prepare Bill Items
        List<CheckoutResponse.BillItem> billItems = items.stream()
                .map(item -> CheckoutResponse.BillItem.builder()
                        .itemName(item.getItemName())
                        .quantity(item.getQuantity())
                        .price(item.getPrice() != null ? item.getPrice() : item.getSubTotal()) // Fallback protect raw base pricing calculations
                        .unit(item.getUnit())
                        .subTotal(item.getSubTotal() != null ? item.getSubTotal() : 0.0) // 🌟 Mapped cleanly as Double
                        .build())
                .toList();

        // 5. Construct and Save CompletedOrder
        CompletedOrder finalBill = CompletedOrder.builder()
                .hotelId(hotelId)
                .orderType("INSTANT")
                .customerName(null)
                .customerMobile(null)
                .customerAddress(null)
                .allOrders(Collections.singletonList(transientOrderSnapshot))
                .grandTotal(grandTotal)
                .paymentStatus("PAID")
                .discountPercent(discountPercent)
                .discountAmount(discountAmount)
                .totalPayable(totalPayable)
                .checkoutBy(actualLoggedInUser)
                .checkoutDate(nowIST.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .checkoutTime(nowIST.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .build();

        CompletedOrder savedBill = completeOrderRepository.save(finalBill);

        // 6. Build Response
        String fullId = savedBill.getId();
        String shortId = (fullId != null && fullId.length() > 6) ? fullId.substring(fullId.length() - 6) : fullId;

        updateDailyAnalytics(hotelId, actualLoggedInUser, totalPayable, items, true);

        return CheckoutResponse.builder()
                .id(fullId)
                .shortId(shortId)
                .checkoutDate(finalBill.getCheckoutDate())
                .checkoutTime(finalBill.getCheckoutTime())
                .orderType("INSTANT")
                .items(billItems)
                .grandTotal(grandTotal)
                .totalPayable(totalPayable)
                .build();
    }

    @Caching(evict = {
            @CacheEvict(value = "dashboardStatsCache", key = "#hotelId"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-today'"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-week'"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-month'"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-year'")
    })
    @Transactional
    public CheckoutResponse instantCustomOrderAndCheckout(String hotelId, InstantCheckoutRequestNew request, String actualLoggedInUser) {
        // 1. Subscription Plan Validation
        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (getISTNow().toLocalDateTime().isAfter(admin.getSubscriptionExpiry())) {
            throw new RuntimeException("Kindly upgrade to the Standard or Premium plan.");
        }

        List<OrderItem> items = request.getItems();
        if (items == null || items.isEmpty()) {
            throw new RuntimeException("Cannot process an instant checkout with an empty items list");
        }

        // Mathematical Computations
        Double grandTotal = items.stream().mapToDouble(OrderItem::getSubTotal).sum();
        Double discountPercent = request.getDiscountPercent() != null ? request.getDiscountPercent() : 0.0;
        Double discountAmount = (grandTotal * discountPercent) / 100.0;
        Double totalPayable = Math.max(0.0, grandTotal - discountAmount);

        ZonedDateTime nowIST = getISTNow();
        String currentDateStr = nowIST.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String currentTimeStr = nowIST.format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        // 2. Build Sanitized In-Memory OrderItems List (Ensuring clean Strings)
        List<OrderItem> sanitizedItems = items.stream().map(item -> {
            String qtyStr = item.getQuantity() != null ? String.valueOf(item.getQuantity()).trim() : "1";
            return OrderItem.builder()
                    .itemName(item.getItemName())
                    .quantity(qtyStr)
                    .price(item.getPrice() != null ? item.getPrice() : 0.0)
                    .subTotal(item.getSubTotal())
                    .build();
        }).toList();

        // 3. Construct the Transient KitchenOrder Document Wrapper
        KitchenOrderSnapshot transientOrderSnapshot = KitchenOrderSnapshot.builder()
                .id(new org.bson.types.ObjectId().toString())
                .items(sanitizedItems)
                .totalAmount(grandTotal)
                .status("COMPLETED")
                .placedBy(actualLoggedInUser)
                .acceptedBy(actualLoggedInUser)
                .completedBy(actualLoggedInUser)
                .updatedAt(nowIST.toLocalDateTime())
                .createdDate(currentDateStr)
                .createdTime(currentTimeStr)
                // Perfect! hotelId, tableName, and orderType are excluded automatically
                .build();

        // 4. Map Response Items (Matching your exact production output model layouts)
        List<CheckoutResponse.BillItem> billItems = sanitizedItems.stream()
                .map(item -> CheckoutResponse.BillItem.builder()
                        .itemName(item.getItemName())
                        .quantity(item.getQuantity()) // Keep as String matching production
                        .price(item.getSubTotal())   // Matches production layout (quantity * unit price)
                        .build())
                .toList();

        // 5. Construct and Save Final CompletedOrder Collection Entry (Enforcing exact production nulls)
        CompletedOrder finalBill = CompletedOrder.builder()
                .hotelId(hotelId)
                .orderType("INSTANT")
                .customerName(null)
                .customerMobile(null)
                .customerAddress(null)
                .allOrders(Collections.singletonList(transientOrderSnapshot))
                .grandTotal(grandTotal)
                .paymentStatus("PAID")
                .discountPercent(discountPercent)
                .discountAmount(discountAmount)
                .totalPayable(totalPayable)
                .checkoutBy(actualLoggedInUser)
                .checkoutDate(currentDateStr)
                .checkoutTime(currentTimeStr)
                .build();

        CompletedOrder savedBill = completeOrderRepository.save(finalBill);

        // 6. Build & Return Standardized CheckoutResponse Payload (Cleaned up to match standard logging)
        String fullId = savedBill.getId();
        String shortId = (fullId != null && fullId.length() > 6) ? fullId.substring(fullId.length() - 6) : fullId;

        updateDailyAnalytics(hotelId, actualLoggedInUser, totalPayable, null, false);

        return CheckoutResponse.builder()
                .id(fullId)
                .shortId(shortId)
                .checkoutDate(finalBill.getCheckoutDate())
                .checkoutTime(finalBill.getCheckoutTime())
                .orderType("INSTANT")
                .items(billItems)
                .grandTotal(grandTotal)
                .totalPayable(totalPayable)
                .build();
    }

    @Caching(evict = {
            @CacheEvict(value = "dashboardStatsCache", key = "#hotelId"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-today'"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-week'"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-month'"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-year'")
    })
    @Transactional
    public CheckoutResponse checkoutDirectOrder(String hotelId, DirectOrderRequest request, String checkoutBy) {
        // 1. Subscription Check (Mirroring standard checkouts)
        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        if (getISTNow().toLocalDateTime().isAfter(admin.getSubscriptionExpiry())) {
            throw new RuntimeException("Your subscription plan has ended.");
        }

        // 2. Validate Direct Request Metrics
        if (request.getTotalAmount() == null || request.getTotalAmount() <= 0) {
            throw new RuntimeException("Invalid total amount for direct checkout");
        }
        if (!org.springframework.util.StringUtils.hasText(request.getItemName())) {
            throw new RuntimeException("Item name cannot be empty for manual logging");
        }

        Double grandTotal = request.getTotalAmount();
        Double discountPercent = (request.getDiscount() != null) ? request.getDiscount() : 0.0;
        Double discountAmount = (grandTotal * discountPercent) / 100;
        Double totalPayable = grandTotal - discountAmount;

        ZonedDateTime nowIST = getISTNow();
        String currentDateStr = nowIST.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String currentTimeStr = nowIST.format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        // 3. Construct Raw Items Array List to match your standard inner model formats
        List<com.raghunath.hotelview.dto.admin.OrderItem> inlineItems = new ArrayList<>();

        // Use the exact setter or builder pattern that your OrderItem class provides
        inlineItems.add(com.raghunath.hotelview.dto.admin.OrderItem.builder()
                .itemName(request.getItemName())
                .quantity(request.getQuantity() != null ? String.valueOf(request.getQuantity()) : "1")
                .price(0.0) // Kept at zero or null per requirement
                .subTotal(grandTotal)
                .build());

        // 4. Construct Nested KitchenOrder Object Structure matching standard schema exactly
        KitchenOrder mockOrder = KitchenOrder.builder()
                .id(new org.bson.types.ObjectId().toString())
                .hotelId(hotelId)
                .tableName(org.springframework.util.StringUtils.hasText(request.getTableName()) ? request.getTableName() : "Counter")
                .orderType(org.springframework.util.StringUtils.hasText(request.getOrderType()) ? request.getOrderType() : "TAKEAWAY")
                .items(inlineItems)
                .totalAmount(grandTotal)
                .status("COMPLETED")
                .placedBy(checkoutBy)
                .completedBy(checkoutBy)
                .updatedAt(nowIST.toLocalDateTime())
                .createdDate(currentDateStr)
                .createdTime(currentTimeStr)
                .build();

        List<KitchenOrderSnapshot> allOrdersList = new ArrayList<>();
        allOrdersList.add(KitchenOrderSnapshot.builder()
                .id(mockOrder.getId())
                .items(mockOrder.getItems())
                .totalAmount(mockOrder.getTotalAmount())
                .status(mockOrder.getStatus())
                .placedBy(mockOrder.getPlacedBy())
                .completedBy(mockOrder.getCompletedBy())
                .updatedAt(mockOrder.getUpdatedAt())
                .createdDate(mockOrder.getCreatedDate())
                .createdTime(mockOrder.getCreatedTime())
                // Omit hotelId, tableName, and orderType from the snapshot structure perfectly!
                .build());

        // 5. Build and Save Completed Order
        CompletedOrder finalBill = CompletedOrder.builder()
                .hotelId(hotelId)
                .orderType(mockOrder.getOrderType())
                .tableName(mockOrder.getTableName())
                .customerName(org.springframework.util.StringUtils.hasText(request.getCustomerName()) ? request.getCustomerName() : "Walk-in Guest")
                .customerMobile(org.springframework.util.StringUtils.hasText(request.getCustomerMobile()) ? request.getCustomerMobile() : "0000000000")
                .customerAddress(org.springframework.util.StringUtils.hasText(request.getCustomerAddress()) ? request.getCustomerAddress() : "N/A")
                .allOrders(allOrdersList)
                .grandTotal(grandTotal)
                .paymentStatus("PAID")
                .discountPercent(discountPercent)
                .discountAmount(discountAmount)
                .totalPayable(totalPayable)
                .checkoutBy(checkoutBy)
                .checkoutDate(currentDateStr)
                .checkoutTime(currentTimeStr)
                .build();

        CompletedOrder savedBill = completeOrderRepository.save(finalBill);

        // 6. Cleanup and Sync
        if (savedBill.getId() != null) {
            syncCustomerDetails(hotelId, finalBill);
            if (org.springframework.util.StringUtils.hasText(request.getTableName())) {
                updateTableBill(hotelId, request.getTableName(), 0.0, true);
            }
        }

        // 7. Build Uniform Response Object
        String fullId = savedBill.getId();
        String shortId = (fullId != null && fullId.length() > 6) ? fullId.substring(fullId.length() - 6) : fullId;

        // Convert string quantity safely into integer to prevent type conversion errors during response building
        int parsedQuantity = 1;
        try {
            parsedQuantity = Integer.parseInt(mockOrder.getItems().get(0).getQuantity());
        } catch (NumberFormatException e) {
            // Fallback safety parameter if a dirty string passes through
            if (request.getQuantity() != null) {
                parsedQuantity = request.getQuantity();
            }
        }

        List<CheckoutResponse.BillItem> responseItems = new ArrayList<>();
        responseItems.add(CheckoutResponse.BillItem.builder()
                .itemName(request.getItemName())
                .quantity(String.valueOf(parsedQuantity)) // ✔️ Cleanly casted primitive int value applied here
                .price(grandTotal)
                .build());

        updateDailyAnalytics(hotelId, checkoutBy, totalPayable, null, false);

        return CheckoutResponse.builder()
                .id(fullId)
                .shortId(shortId)
                .checkoutDate(finalBill.getCheckoutDate())
                .checkoutTime(finalBill.getCheckoutTime())
                .orderType(finalBill.getOrderType())
                .tableName(finalBill.getTableName())
                .customerName(finalBill.getCustomerName())
                .customerMobile(finalBill.getCustomerMobile())
                .customerAddress(finalBill.getCustomerAddress())
                .items(responseItems)
                .grandTotal(grandTotal)
                .totalPayable(totalPayable)
                .discountPercent(discountPercent) // 🌟 FIXED: Added missing field
                .discountAmount(discountAmount)
                .restaurantName(admin.getRestaurantName())
                .restaurantAddress(admin.getRestaurantAddress())
                .restaurantContact(admin.getRestaurantContact())
                .restaurantUpi(admin.getRestaurantUpi())
                .restaurantLogo(admin.getRestaurantLogo())
                .build();
    }

    private void updateDailyAnalytics(String hotelId, String checkoutBy, Double totalPayable, List<OrderItem> items, boolean trackItems) {
        try {
            if (totalPayable == null || totalPayable <= 0) return;

            ZonedDateTime nowIST = getISTNow();
            String todayStr = nowIST.format(DateTimeFormatter.ISO_LOCAL_DATE); // Format: "YYYY-MM-DD"

            // =========================================================================
            // 1. UPDATE STAFF COLLECTION SUMMARY
            // =========================================================================
            String staffDocId = hotelId + "_" + todayStr + "_STAFF";

            // Resolve Staff / Employee Identifier details
            String staffIdentifier = checkoutBy != null ? checkoutBy : "SYSTEM";
            String staffDisplayName = staffIdentifier;

            // Try resolving staff name/ID via Employee collection lookup
            Optional<Employee> empOpt = mongoTemplate.find(
                    new Query(Criteria.where("hotelId").is(hotelId).and("username").is(staffIdentifier)),
                    Employee.class,
                    "employees"
            ).stream().findFirst();

            if (empOpt.isPresent()) {
                Employee emp = empOpt.get();
                staffIdentifier = emp.getId() != null ? emp.getId() : emp.getUsername();
                staffDisplayName = emp.getName() != null ? emp.getName() : emp.getUsername();
            }

            // A. Atomic update: Try to increment existing staff member entry in the array
            Query staffArrayMatchQuery = new Query(Criteria.where("_id").is(staffDocId).and("staff.staffId").is(staffIdentifier));
            Update incExistingStaff = new Update()
                    .inc("totalCollection", totalPayable)
                    .inc("staff.$.amount", totalPayable);

            UpdateResult staffUpdateResult = mongoTemplate.updateFirst(staffArrayMatchQuery, incExistingStaff, "daily_staff_summary");

            // B. If staff does not exist in array yet, push them into array (or create daily doc if 1st order)
            if (staffUpdateResult.getMatchedCount() == 0) {
                Query baseStaffDocQuery = new Query(Criteria.where("_id").is(staffDocId));

                // Build raw Document to match your requested structure exactly
                org.bson.Document newStaffElement = new org.bson.Document()
                        .append("staffId", staffIdentifier)
                        .append("name", staffDisplayName)
                        .append("amount", totalPayable);

                Update pushNewStaff = new Update()
                        .setOnInsert("restaurantId", hotelId)
                        .setOnInsert("date", todayStr)
                        .inc("totalCollection", totalPayable)
                        .push("staff", newStaffElement);

                mongoTemplate.upsert(baseStaffDocQuery, pushNewStaff, "daily_staff_summary");
            }

            // =========================================================================
            // 2. UPDATE ITEM SALES SUMMARY (Skipped for custom/direct orders)
            // =========================================================================
            if (trackItems && items != null && !items.isEmpty()) {
                String itemDocId = hotelId + "_" + todayStr + "_ITEMS";

                for (OrderItem item : items) {
                    // Filter out Custom items: Only process items that have a valid DB itemId
                    if (item.getItemId() == null || item.getItemId().trim().isEmpty()) {
                        continue;
                    }

                    String itemId = item.getItemId().trim();
                    String rawItemName = item.getItemName() != null ? item.getItemName().trim() : "Unknown Item";
                    String unitStr = item.getUnit() != null ? item.getUnit().trim() : "PIECE";

                    // Parse quantity safely (handles "2", "2.0", "1 Litre", etc.)
                    double numericQty = 1.0;
                    if (item.getQuantity() != null) {
                        try {
                            String cleanQty = item.getQuantity().replaceAll("[^0-9.]", "");
                            if (!cleanQty.isEmpty()) {
                                numericQty = Double.parseDouble(cleanQty);
                            }
                        } catch (Exception ignored) {}
                    }

                    // A. Atomic update: Try to increment existing item entry in the array
                    Query itemArrayMatchQuery = new Query(Criteria.where("_id").is(itemDocId).and("items.itemId").is(itemId));
                    Update incExistingItem = new Update()
                            .inc("totalQtySold", numericQty)
                            .inc("items.$.qty", numericQty);

                    UpdateResult itemUpdateResult = mongoTemplate.updateFirst(itemArrayMatchQuery, incExistingItem, "daily_item_summary");

                    // B. If item does not exist in array yet, push it into array
                    if (itemUpdateResult.getMatchedCount() == 0) {
                        Query baseItemDocQuery = new Query(Criteria.where("_id").is(itemDocId));

                        org.bson.Document newItemElement = new org.bson.Document()
                                .append("itemId", itemId)
                                .append("name", rawItemName)
                                .append("unit", unitStr)
                                .append("qty", numericQty);

                        Update pushNewItem = new Update()
                                .setOnInsert("restaurantId", hotelId)
                                .setOnInsert("date", todayStr)
                                .inc("totalQtySold", numericQty)
                                .push("items", newItemElement);

                        mongoTemplate.upsert(baseItemDocQuery, pushNewItem, "daily_item_summary");
                    }
                }
            }
        } catch (Exception e) {
            log.error("DAILY_ANALYTICS_SYNC_ERROR: Failed updating analytics for hotelId: {}", hotelId, e);
        }
    }

    /**
     * ACCEPT CUSTOMER QR ORDER (Admin/Employee Action)
     * Validates customer QR order, creates an entry in kitchen_orders with "placedBy" details,
     * updates table bill balance, and DELETES the staging record from customer_table_orders.
     */
    @Caching(evict = {
            @CacheEvict(value = "dashboardStatsCache", key = "#hotelId"),
            @CacheEvict(value = "orderCache", key = "#hotelId + '-today'")
    })
    @Transactional
    public KitchenOrder acceptCustomerOrder(String hotelId, String customerOrderId, String staffId) {

        // 1. Fetch pending customer QR order from staging repository
        CustomerOrder cOrder = customerOrderRepository.findById(customerOrderId)
                .orElseThrow(() -> new RuntimeException("Customer order not found with ID: " + customerOrderId));

        if (!cOrder.getHotelId().equals(hotelId)) {
            throw new RuntimeException("Unauthorized: Order does not belong to this hotel");
        }

        // 2. Create KitchenOrder for Kitchen Display System (KDT)
        KitchenOrder kOrder = KitchenOrder.builder()
                .hotelId(cOrder.getHotelId())
                .tableName(cOrder.getTableName())
                .orderType(StringUtils.hasText(cOrder.getOrderType()) ? cOrder.getOrderType() : "TABLE")
                .items(cOrder.getItems())
                .totalAmount(cOrder.getTotalAmount())
                .status("PENDING") // Pushed to kitchen
                .comments(cOrder.getComments())
                .placedBy("CUSTOMER (Accepted by " + staffId + ")") // 🌟 Preserves audit trail in KitchenOrder
                .createdDate(cOrder.getCreatedDate())
                .createdTime(cOrder.getCreatedTime())
                .build();

        KitchenOrder savedKitchenOrder = kitchenOrderRepository.save(kOrder);

        // 3. Update Table Visual Status & Active Bill Balance
        if (StringUtils.hasText(cOrder.getTableName())) {
            updateTableVisualStatus(hotelId, cOrder.getTableName(), "PENDING");
            updateTableBill(hotelId, cOrder.getTableName(), cOrder.getTotalAmount(), false);
        }

        // 4. 🌟 CLEANUP: Remove from staging collection since it is now a standard live order
        customerOrderRepository.delete(cOrder);

        versionService.bumpTables(hotelId);

        return savedKitchenOrder;
    }

    private void reduceInventoryStock(String hotelId, List<OrderItem> items) {
        if (items == null || items.isEmpty()) return;

        for (OrderItem item : items) {
            if (item.getItemName() == null) continue;

            String sanitizedName = item.getItemName().trim();
            com.raghunath.hotelview.entity.MenuItem menuItem = null;

            // 🌟 1. PRIORITY CHECK: Try identifying the exact database document using its unique itemId snapshot
            if (item.getItemId() != null && !item.getItemId().trim().isEmpty()) {
                Optional<?> findByIdOpt = menuItemRepository.findById(item.getItemId().trim());
                if (findByIdOpt.isPresent() && findByIdOpt.get() instanceof com.raghunath.hotelview.entity.MenuItem dbItem) {
                    menuItem = dbItem;
                }
            }

            // 🌟 2. FALLBACK CHECK: Fixed to safely pull the first matching element instead of throwing a non-unique 500
            if (menuItem == null) {
                // Using a regex find sequence allows us to safely handle duplicates without throwing an internal exception
                List<com.raghunath.hotelview.entity.MenuItem> fallbackList = mongoTemplate.find(
                        new Query(Criteria.where("hotelId").is(hotelId)
                                .and("name").regex("^" + java.util.regex.Pattern.quote(sanitizedName) + "$", "i")),
                        com.raghunath.hotelview.entity.MenuItem.class,
                        "menu_items"
                );

                if (!fallbackList.isEmpty()) {
                    // Prioritize an active/available version if duplicates exist; otherwise grab the first index entry
                    menuItem = fallbackList.stream()
                            .filter(mi -> mi != null && Boolean.TRUE.equals(mi.getIsAvailable()))
                            .findFirst()
                            .orElse(fallbackList.get(0));
                }
            }

            // 🌟 3. ORIGINAL CALCULATIONS AND ATOMIC INCREMENT ACTIONS BLOCK
            if (menuItem != null) {
                // RESOLVE QUANTITY METRIC FIRST: If quantity is explicitly 0, skip deduction entirely
                String incomingQtyStr = item.getQuantity() != null ? item.getQuantity().trim().toLowerCase() : "1";
                double extractedValue = 1.0;
                double finalDeduction = 0.0;

                try {
                    String numericPart = incomingQtyStr.replaceAll("[^0-9.]", "");
                    if (org.springframework.util.StringUtils.hasText(numericPart)) {
                        extractedValue = Double.parseDouble(numericPart);
                    }
                } catch (Exception e) {
                    log.error("INVENTORY_PARSE_ERR: Failed parsing quantity string '{}'", incomingQtyStr);
                }

                // Quick bypass optimization check for empty orders or tracking entries holding zero values
                if (extractedValue <= 0.0) {
                    continue;
                }

                // Evaluate fallback variations across your document schema fields
                String resolvedUnit = "PIECE";
                if (menuItem.getUnit() != null) {
                    resolvedUnit = menuItem.getUnit();
                } else if (menuItem.getTotalquantityUnit() != null) {
                    resolvedUnit = menuItem.getTotalquantityUnit();
                }

                String adminUnit = resolvedUnit.toUpperCase();

                // ENHANCED CONVERSION MATRIX: Retained perfectly from your original source code
                if (incomingQtyStr.contains("ml") && "ML".equals(adminUnit)) {
                    finalDeduction = extractedValue;
                } else if (incomingQtyStr.contains("ml") && "LITER".equals(adminUnit)) {
                    finalDeduction = extractedValue / 1000.0;
                } else if (incomingQtyStr.contains("gm") && "GM".equals(adminUnit)) {
                    finalDeduction = extractedValue;
                } else if (incomingQtyStr.contains("gm") && "KG".equals(adminUnit)) {
                    finalDeduction = extractedValue / 1000.0;
                } else {
                    finalDeduction = extractedValue;
                }

                // Execute atomic increment directly using target item unique Object Identifier
                try {
                    // Targeting by the unique Object ID is faster and protects against name duplicate collisions completely
                    Query query = new Query(Criteria.where("_id").is(menuItem.getId()));
                    Update update = new Update().inc("totalQuantity", -finalDeduction);

                    mongoTemplate.updateFirst(query, update, com.raghunath.hotelview.entity.MenuItem.class);
                    log.info("INVENTORY_LOG: Atomic deduction forced for '{}': -{}", menuItem.getName(), finalDeduction);
                } catch (Exception e) {
                    log.error("INVENTORY_CRITICAL_ERR: Direct atomic inventory adjustment failed for '{}'", sanitizedName, e);
                }
            } else {
                log.error("INVENTORY_LOG: Skipped tracking update. Name '{}' not matching items in hotel: {}", sanitizedName, hotelId);
            }
        }
    }

    /**
     * 8. FETCH DASHBOARD STATS
     */
    @Cacheable(value = "dashboardStatsCache", key = "#hotelId")
    public DashboardStatsDTO getDashboardStats(String hotelId) {
        ZonedDateTime nowIST = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        String todayDate = nowIST.format(DateTimeFormatter.ISO_LOCAL_DATE);

        Admin admin = adminRepository.findByHotelId(hotelId)
                .orElseThrow(() -> new RuntimeException("Hotel Admin not found"));

        ZonedDateTime last24HoursStart = nowIST.minusHours(24);
        String last24HoursStartDate = last24HoursStart.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String last24HoursStartTime = last24HoursStart.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String nowTime = nowIST.format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        String restaurantName = admin.getRestaurantName() != null ? admin.getRestaurantName() : "Unknown Restaurant";
        String planType = admin.getPlanType() != null ? admin.getPlanType() : "BASIC";

        List<String> activeStatuses = List.of("PENDING", "ACCEPTED", "ACTIVE");
        Long activeTablesCount = tableRepository.countByHotelIdAndStatusIn(hotelId, activeStatuses);

        List<String> deliveryTypes = List.of("HOME", "PARCEL");
        Long homeAndParcelOrdersToday = completeOrderRepository.countByHotelIdAndOrderTypeInAndCheckoutDate(
                hotelId, deliveryTypes, todayDate);

        List<String> deliveryType = List.of("INSTANT");
        Long instantOrdersToday = completeOrderRepository.countByHotelIdAndOrderTypeInAndCheckoutDate(hotelId, deliveryType, todayDate);

        Long employeeCount = employeeRepository.countByHotelIdAndIsActive(hotelId, true);
        Long totalItems = menuItemRepository.countByHotelId(hotelId);
        Long completedTodayCount = completeOrderRepository.countByHotelIdAndCheckoutDate(hotelId, todayDate);

        Double todaySalesRupees = 0.0;
        try {
            Double result = completeOrderRepository.sumTotalPayableByHotelIdAndCheckoutDate(hotelId, todayDate);
            todaySalesRupees = (result != null) ? result : 0.0;
        } catch (Exception e) {
            log.error("AGGREGATION_ERROR: Sales sum failed for hotel {}", hotelId);
        }

        Double last24HoursSales = 0.0;
        Long last24HoursOrders = 0L;
        try {
            List<CompletedOrder> last24HoursCompletedOrders = mongoTemplate.find(
                    new Query(Criteria.where("hotelId").is(hotelId)
                            .orOperator(
                                    Criteria.where("checkoutDate").is(todayDate),
                                    Criteria.where("checkoutDate").is(last24HoursStartDate)
                            )),
                    CompletedOrder.class
            );

            last24HoursOrders = last24HoursCompletedOrders.stream()
                    .filter(o -> isWithinLast24Hours(o, last24HoursStart, nowIST))
                    .count();

            last24HoursSales = last24HoursCompletedOrders.stream()
                    .filter(o -> isWithinLast24Hours(o, last24HoursStart, nowIST))
                    .mapToDouble(o -> o.getTotalPayable() != null ? o.getTotalPayable() : 0.0)
                    .sum();
        } catch (Exception e) {
            log.error("AGGREGATION_ERROR: Last 24h stats failed for hotel {}", hotelId);
        }

        return DashboardStatsDTO.builder()
                .activeTablesCount(activeTablesCount)
                .HomeDeliveriesCount(homeAndParcelOrdersToday)
                .InstantCount(instantOrdersToday)
                .completedOrdersTodayCount(completedTodayCount)
                .employeeOnlineCount(employeeCount)
                .totalItemsCount(totalItems)
                .restaurantName(restaurantName)
                .planType(planType)
                .todaySalesRupees(todaySalesRupees)
                .last24HoursSalesRupees(last24HoursSales)
                .last24HoursOrdersCount(last24HoursOrders)
                .restaurantAddress(admin.getRestaurantAddress())    // ← ADD
                .restaurantContact(admin.getRestaurantContact())    // ← ADD
                .restaurantUpi(admin.getRestaurantUpi())
                .restaurantLogo(admin.getRestaurantLogo())
                .build();
    }

    private boolean isWithinLast24Hours(CompletedOrder order, ZonedDateTime from, ZonedDateTime to) {
        try {
            // Combine checkoutDate + checkoutTime into a ZonedDateTime for comparison
            LocalDateTime orderDateTime = LocalDateTime.parse(
                    order.getCheckoutDate() + "T" + order.getCheckoutTime()
            );
            ZonedDateTime orderZDT = orderDateTime.atZone(IST);
            return !orderZDT.isBefore(from) && !orderZDT.isAfter(to);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 9. EDIT ORDER
     */
    /**
     * CONFIRM ORDER EDIT: Applies active item edits to a live KitchenOrder,
     * tracks precise logs, updates the table bill delta shift, and maps unit configurations.
     */
    @Transactional
    public void confirmOrderEdit(String hotelId, String orderId, List<OrderItem> newItems, String editedBy) {
        KitchenOrder order = kitchenOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        List<OrderEdit> editLogs = new ArrayList<>();
        String timeIST = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // 🌟 PRE-STEP: Sanitize names, quantities, and map units inside the incoming list inline
        if (newItems != null) {
            newItems.forEach(item -> {
                if (item.getItemName() != null) {
                    item.setItemName(item.getItemName().contains("//") ? item.getItemName().split("//")[0].trim() : item.getItemName().trim());
                }
                if (item.getQuantity() != null) {
                    item.setQuantity(item.getQuantity().contains("//") ? item.getQuantity().split("//")[0].trim() : item.getQuantity().trim());
                }
                // Directly capture and clean the unit field from frontend payload layout
                if (item.getUnit() == null || item.getUnit().trim().isEmpty()) {
                    item.setUnit("PIECE");
                } else {
                    item.setUnit(item.getUnit().contains("//") ? item.getUnit().split("//")[0].trim() : item.getUnit().trim());
                }
            });
        }

        // Loop 1: Check updates or removals against existing items
        for (OrderItem oldItem : order.getItems()) {
            String cleanOldName = oldItem.getItemName() != null ? oldItem.getItemName().trim() : "";
            cleanOldName = cleanOldName.contains("//") ? cleanOldName.split("//")[0].trim() : cleanOldName;

            String finalCleanOldName = cleanOldName;
            OrderItem newItem = newItems.stream()
                    .filter(ni -> ni.getItemName().equalsIgnoreCase(finalCleanOldName))
                    .findFirst()
                    .orElse(null);

            String oldQtyStr = (oldItem.getQuantity() != null) ? oldItem.getQuantity() : "0";
            String newQtyStr = (newItem != null && newItem.getQuantity() != null) ? newItem.getQuantity() : "0";

            if (!oldQtyStr.equalsIgnoreCase(newQtyStr)) {
                String cleanOld = oldQtyStr.replaceAll("[^0-9.]", "");
                String cleanNew = newQtyStr.replaceAll("[^0-9.]", "");

                int prev = cleanOld.isEmpty() ? 0 : (int) Double.parseDouble(cleanOld);
                int next = cleanNew.isEmpty() ? 0 : (int) Double.parseDouble(cleanNew);

                editLogs.add(OrderEdit.builder()
                        .orderId(orderId)
                        .hotelId(hotelId)
                        .editedBy(editedBy)
                        .itemName(oldItem.getItemName())
                        .previousQty(prev)
                        .newQty(next)
                        .delta(next - prev)
                        .timestamp(timeIST)
                        .build());
            }
        }

        // Loop 2: Log entirely brand new additions
        for (OrderItem newItem : newItems) {
            boolean exists = order.getItems().stream()
                    .anyMatch(old -> old.getItemName().equalsIgnoreCase(newItem.getItemName()));

            if (!exists) {
                String newQtyStr = (newItem.getQuantity() != null) ? newItem.getQuantity() : "0";
                String cleanNew = newQtyStr.replaceAll("[^0-9.]", "");
                int next = cleanNew.isEmpty() ? 0 : (int) Double.parseDouble(cleanNew);

                editLogs.add(OrderEdit.builder()
                        .orderId(orderId)
                        .hotelId(hotelId)
                        .editedBy(editedBy)
                        .itemName(newItem.getItemName())
                        .previousQty(0)
                        .newQty(next)
                        .delta(next)
                        .timestamp(timeIST)
                        .build());
            }
        }

        double oldTotal = order.getTotalAmount();
        double newTotal = newItems != null ? newItems.stream().mapToDouble(OrderItem::getSubTotal).sum() : 0.0;

        order.setItems(newItems);
        order.setTotalAmount(newTotal);
        kitchenOrderRepository.save(order);

        updateTableBill(hotelId, order.getTableName(), (newTotal - oldTotal), false);

        if (!editLogs.isEmpty()) {
            mongoTemplate.insert(editLogs, "order_edits");
        }

        versionService.bumpTables(hotelId);
    }

    /**
     * 10. FETCH TODAY'S SUMMARY DATA
     */
    public DailySummaryResponse getTodaySummary(String hotelId) {
        String todayStr = getISTNow().format(DateTimeFormatter.ISO_LOCAL_DATE);
        return getSummaryByDate(hotelId, todayStr);
    }

    /**
     * 11. FETCH DATE-WISE SUMMARY DATA (e.g. "2026-07-29")
     */
    public DailySummaryResponse getSummaryByDate(String hotelId, String date) {
        String staffDocId = hotelId + "_" + date + "_STAFF";
        String itemDocId = hotelId + "_" + date + "_ITEMS";

        // Fetch Staff Collection Document
        org.bson.Document staffDoc = mongoTemplate.findById(staffDocId, org.bson.Document.class, "daily_staff_summary");

        // Fetch Item Sales Document
        org.bson.Document itemDoc = mongoTemplate.findById(itemDocId, org.bson.Document.class, "daily_item_summary");

        // Map Staff Summary
        Double totalStaffColl = 0.0;
        List<DailySummaryResponse.StaffSummaryDto> staffList = new ArrayList<>();

        if (staffDoc != null) {
            totalStaffColl = staffDoc.getDouble("totalCollection") != null ? staffDoc.getDouble("totalCollection") : 0.0;
            List<org.bson.Document> rawStaff = staffDoc.getList("staff", org.bson.Document.class);
            if (rawStaff != null) {
                staffList = rawStaff.stream().map(d -> DailySummaryResponse.StaffSummaryDto.builder()
                        .staffId(d.getString("staffId"))
                        .name(d.getString("name"))
                        .amount(d.getDouble("amount") != null ? d.getDouble("amount") : 0.0)
                        .build()).toList();
            }
        }

        // Map Item Summary
        Double totalQty = 0.0;
        List<DailySummaryResponse.ItemSummaryDto> itemList = new ArrayList<>();

        if (itemDoc != null) {
            totalQty = itemDoc.getDouble("totalQtySold") != null ? itemDoc.getDouble("totalQtySold") : 0.0;
            List<org.bson.Document> rawItems = itemDoc.getList("items", org.bson.Document.class);
            if (rawItems != null) {
                itemList = rawItems.stream().map(d -> DailySummaryResponse.ItemSummaryDto.builder()
                        .itemId(d.getString("itemId"))
                        .name(d.getString("name"))
                        .unit(d.getString("unit"))
                        .qty(d.getDouble("qty") != null ? d.getDouble("qty") : 0.0)
                        .build()).toList();
            }
        }

        return DailySummaryResponse.builder()
                .date(date)
                .restaurantId(hotelId)
                .totalStaffCollection(totalStaffColl)
                .staffSummary(staffList)
                .totalQtySold(totalQty)
                .itemSummary(itemList)
                .build();
    }

    /**
     * 12. Get Full Table History via Completed Order ID
     */
    public Map<String, Object> getAggregatedTableSummary(String hotelId, String completedOrderId) {
        CompletedOrder completedBill = mongoTemplate.findById(completedOrderId, CompletedOrder.class);

        if (completedBill == null) {
            throw new RuntimeException("Completed Order record not found");
        }

        Map<String, Object> finalTableHistory = new LinkedHashMap<>();

        for (KitchenOrderSnapshot subOrder : completedBill.getAllOrders()) {
            String subOrderId = subOrder.getId();

            Query query = new Query(Criteria.where("orderId").is(subOrderId).and("hotelId").is(hotelId));
            query.with(Sort.by(Sort.Direction.ASC, "timestamp"));

            List<OrderEdit> editHistory = mongoTemplate.find(query, OrderEdit.class, "order_edits");

            Map<String, Object> orderEntry = new LinkedHashMap<>();
            orderEntry.put("orderType", completedBill.getOrderType());
            orderEntry.put("finalItems", subOrder.getItems());
            orderEntry.put("totalAmount", subOrder.getTotalAmount());

            // Resolve names for the sub-order lifecycles instead of returning raw Hex IDs
            orderEntry.put("placedBy", getUserRealName(subOrder.getPlacedBy()));
            orderEntry.put("acceptedBy", getUserRealName(subOrder.getAcceptedBy()));
            orderEntry.put("completedBy", getUserRealName(subOrder.getCompletedBy()));

            if (editHistory.isEmpty()) {
                orderEntry.put("editSummary", "No order edit summary available");
            } else {
                // Transform edit history maps dynamically to swap "editedBy" IDs for names
                List<Map<String, Object>> dynamicEditSummaryList = new ArrayList<>();
                for (OrderEdit edit : editHistory) {
                    Map<String, Object> editMap = new LinkedHashMap<>();
                    editMap.put("id", edit.getId());
                    editMap.put("orderId", edit.getOrderId());
                    editMap.put("hotelId", edit.getHotelId());

                    // Resolve the dynamic editor ID into their plain text name string here!
                    editMap.put("editedBy", getUserRealName(edit.getEditedBy()));

                    editMap.put("itemName", edit.getItemName());
                    editMap.put("previousQty", edit.getPreviousQty());
                    editMap.put("newQty", edit.getNewQty());
                    editMap.put("delta", edit.getDelta());
                    editMap.put("timestamp", edit.getTimestamp());

                    dynamicEditSummaryList.add(editMap);
                }
                orderEntry.put("editSummary", dynamicEditSummaryList);
            }

            finalTableHistory.put("Order_" + subOrderId, orderEntry);
        }

        // Resolve checkout context identity name at root level termination
        finalTableHistory.put("checkoutBy", getUserRealName(completedBill.getCheckoutBy()));

        return finalTableHistory;
    }

    /**
     * Helper method to perform cross-collection name resolution for dynamic user IDs.
     * Searches the Employee collection first, falls back to Admin, then defaults to the ID.
     */
    private String getUserRealName(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return "N/A";
        }

        // 1. Check if the action was executed by an Employee
        org.bson.Document employeeDoc = mongoTemplate.findOne(
                new Query(Criteria.where("_id").is(userId)),
                org.bson.Document.class,
                "employees" // Make sure this matches your exact MongoDB employee collection name
        );
        if (employeeDoc != null && employeeDoc.containsKey("name")) {
            return employeeDoc.getString("name");
        }

        // 2. Fallback: Check if the action was executed by an Admin user
        org.bson.Document adminDoc = mongoTemplate.findOne(
                new Query(Criteria.where("_id").is(userId)),
                org.bson.Document.class,
                "admins" // Make sure this matches your exact MongoDB admin collection name
        );
        if (adminDoc != null && adminDoc.containsKey("name")) {
            return adminDoc.getString("name");
        }

        // 3. Fallback: Check Admin by user handling custom alternative identifier mappings if applicable
        Admin adminByHotel = mongoTemplate.findOne(
                new Query(Criteria.where("hotelId").is(userId)),
                Admin.class
        );
        if (adminByHotel != null) {
            return adminByHotel.getName();
        }

        // If no record is found anywhere, return the original ID safely instead of crashing
        return userId;
    }

    public void processExternalOrder(OrderWebhookDTO dto) {
        if (externalOrderRepository.existsByExternalOrderId(dto.getExternalOrderId())) {
            System.out.println("Duplicate order " + dto.getExternalOrderId() + " ignored.");
            return;
        }

        Admin admin = adminRepository.findByHotelId(dto.getHotelId())
                .orElseThrow(() -> new RuntimeException("Hotel not found"));

        String incomingMerchantId = dto.getMerchantId();
        String storedMerchantId = admin.getPlatformIds().get(dto.getPlatformName().toUpperCase());

        if (storedMerchantId == null || !storedMerchantId.equals(incomingMerchantId)) {
            throw new RuntimeException("Unauthorized: Merchant ID mismatch");
        }

        List<OrderItem> internalItems = dto.getItems().stream().map(extItem -> {
            OrderItem item = new OrderItem();
            item.setItemId("EXTERNAL");
            item.setItemName(extItem.getItemName());
            item.setQuantity(String.valueOf(extItem.getQuantity()));
            item.setPrice(extItem.getPrice());
            item.setSubTotal(extItem.getSubTotal());
            return item;
        }).collect(Collectors.toList());

        ExternalOrder externalOrder = ExternalOrder.builder()
                .hotelId(dto.getHotelId())
                .platform(dto.getPlatformName())
                .externalOrderId(dto.getExternalOrderId())
                .customerName(dto.getCustomerName())
                .customerMobile(dto.getCustomerContact())
                .deliveryAddress(dto.getDeliveryAddress())
                .items(internalItems)
                .totalAmount(dto.getTotalAmount())
                .status("RECEIVED")
                .receivedAt(LocalDateTime.now())
                .build();

        ExternalOrder savedOrder = externalOrderRepository.save(externalOrder);

        messagingTemplate.convertAndSend("/topic/orders/" + dto.getHotelId(), savedOrder);

        System.out.println("New " + dto.getPlatformName() + " order saved with ID: " + savedOrder.getId());
    }

    @Transactional
    public String approveExternalOrder(String orderIdIdentifier, String approvedBy) {
        ExternalOrder ext = externalOrderRepository.findById(orderIdIdentifier)
                .orElseGet(() -> externalOrderRepository.findByExternalOrderId(orderIdIdentifier)
                        .orElseThrow(() -> new RuntimeException("External order not found: " + orderIdIdentifier)));

        if ("ACCEPTED".equals(ext.getStatus())) {
            throw new RuntimeException("Order is already accepted");
        }

        ext.setStatus("ACCEPTED");
        externalOrderRepository.save(ext);

        KitchenOrder kitchenOrder = KitchenOrder.builder()
                .hotelId(ext.getHotelId())
                .tableName(null)
                .orderType("EXTERNAL_" + ext.getPlatform())
                .items(ext.getItems())
                .totalAmount(ext.getTotalAmount())
                .status("PENDING")
                .placedBy(approvedBy)
                .comments("DELIVERY: " + ext.getCustomerName() + " | " + ext.getCustomerMobile())
                .build();

        KitchenOrder saved = kitchenOrderRepository.save(kitchenOrder);

        versionService.bumpTables(ext.getHotelId());

        return saved.getId();
    }

    private void syncCustomerDetails(String hotelId, CompletedOrder bill) {
        String mobile = bill.getCustomerMobile();

        if (StringUtils.hasText(mobile) && !"0000000000".equals(mobile)) {

            Query query = new Query(Criteria.where("hotelId").is(hotelId)
                    .and("customerMobile").is(mobile));

            Update update = new Update()
                    .set("customerName", bill.getCustomerName())
                    .set("customerAddress", bill.getCustomerAddress())
                    .set("lastOrderDate", bill.getCheckoutDate())
                    .inc("totalOrders", 1)
                    .inc("totalAmountPaid", bill.getTotalPayable());

            mongoTemplate.upsert(query, update, CustomerDetails.class);
        }
    }

    private void updateTableVisualStatus(String hotelId, String tableName, String status) {
        if (tableName == null) return;

        tableRepository.findByHotelIdAndTableName(hotelId, tableName).ifPresent(t -> {
            t.setStatus(status);
            t.setUpdatedAt(LocalDateTime.now());
            tableRepository.save(t);
        });
    }

    private void updateTableBill(String hotelId, String tableName, Double amountToAdd, boolean isReset) {
        if (tableName == null) return;

        tableRepository.findByHotelIdAndTableName(hotelId, tableName).ifPresent(table -> {
            if (isReset) {
                table.setCurrentBill(0.0);
                table.setStatus("INACTIVE");
            } else {
                Double existingBill = table.getCurrentBill() != null ? table.getCurrentBill() : 0.0;
                double newTotal = Math.max(0.0, existingBill + (amountToAdd != null ? amountToAdd : 0.0));
                table.setCurrentBill(newTotal);

                if (newTotal <= 0) {
                    table.setStatus("INACTIVE");
                } else {
                    table.setStatus("PENDING");
                }
            }
            table.setUpdatedAt(LocalDateTime.now());
            tableRepository.save(table);
            log.info("BILL_SYNC: Hotel {} Table {} updated to {}. Status: {}",
                    hotelId, tableName, table.getCurrentBill(), table.getStatus());
        });
    }

    public SalesAnalyticsDTO getTodayHourlySales(String hotelId) {
        return salesAggregationRepository.getTodayAnalytics(hotelId);
    }

    public SalesAnalyticsDTO getCurrentWeekSales(String hotelId) {
        return salesAggregationRepository.getWeekAnalytics(hotelId);
    }

    public SalesAnalyticsDTO getCurrentMonthSales(String hotelId) {
        return salesAggregationRepository.getMonthAnalytics(hotelId);
    }

    public SalesAnalyticsDTO getCurrentYearSales(String hotelId) {
        return salesAggregationRepository.getYearAnalytics(hotelId);
    }

    private ZonedDateTime getISTNow() {
        return ZonedDateTime.now(IST);
    }
}
