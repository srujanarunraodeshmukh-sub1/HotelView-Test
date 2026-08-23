package com.raghunath.hotelview.controller.restaurantandinstantadmin;

import com.raghunath.hotelview.entity.restaurantandinstant.RestaurantTable;
import com.raghunath.hotelview.security.UserTypeAccessGuard;
import com.raghunath.hotelview.service.restaurantandinstantadmin.OrderService;
import com.raghunath.hotelview.service.restaurantandinstantadmin.TableService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tables")
@RequiredArgsConstructor
public class TableController {

    private final TableService tableService;
    private final OrderService orderService;
    private final UserTypeAccessGuard userTypeAccessGuard;

    private String getHotelId() {
        return userTypeAccessGuard.enforceUserTypeForCurrentUser("RESTAURANT");
    }

    // 1. Get all tables for the logged-in Hotel
    @GetMapping
    public List<RestaurantTable> getMyTables() {
        String hotelId = getHotelId();
        return tableService.getAllTables(hotelId);
    }

    // 2. Add a new table manually
    @PostMapping("/add")
    public ResponseEntity<RestaurantTable> addTable(@RequestBody RestaurantTable table) {
        String hotelId = getHotelId();
        return ResponseEntity.ok(tableService.saveTable(hotelId, table));
    }

    @PutMapping("/transfer/{fromTable}/{toTable}")
    public ResponseEntity<String> transferTableOrders(
            @PathVariable String fromTable,
            @PathVariable String toTable) {
        String hotelId = getHotelId();
        tableService.transferTableOrders(hotelId, fromTable, toTable);
        return ResponseEntity.ok("Orders transferred to Table " + toTable + " successfully");
    }

    @PutMapping("/{id}")
    public ResponseEntity<RestaurantTable> updateTable(
            @PathVariable String id,
            @RequestBody RestaurantTable tableDetails) {
        String hotelId = getHotelId();
        return ResponseEntity.ok(tableService.updateTable(id, hotelId, tableDetails));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteTable(@PathVariable String id) {
        String hotelId = getHotelId();

        try {
            tableService.deleteTable(id, hotelId); // 👈 Variables match service parameters perfectly now
            return ResponseEntity.ok("Table deleted successfully");
        } catch (RuntimeException e) {
            // Return a clean message if the table has an unpaid bill or doesn't belong to them
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

}