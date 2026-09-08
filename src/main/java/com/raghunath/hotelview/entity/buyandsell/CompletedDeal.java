package com.raghunath.hotelview.entity.buyandsell;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "completed_deals")
public class CompletedDeal {

    @Id
    private String id;

    private String dealerId;
    private String itemId;
    private Item item; // full snapshot of the sold item at time of sale

    private ClientInfo client;
    private InsuranceInfo insurance;

    private LocalDateTime dealDate;
    private long version;
    private boolean deleted;
    private String clientId;

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public static class ClientInfo {
        private String name;
        private String mobile;
        private String address;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getMobile() { return mobile; }
        public void setMobile(String mobile) { this.mobile = mobile; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
    }

    public static class InsuranceInfo {
        private String name;
        private String mobile;
        private String lapseDate;
        private String vehicleNumber;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getMobile() { return mobile; }
        public void setMobile(String mobile) { this.mobile = mobile; }
        public String getLapseDate() { return lapseDate; }
        public void setLapseDate(String lapseDate) { this.lapseDate = lapseDate; }
        public String getVehicleNumber() { return vehicleNumber; }
        public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }
    }

    // getters/setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDealerId() { return dealerId; }
    public void setDealerId(String dealerId) { this.dealerId = dealerId; }
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public Item getItem() { return item; }
    public void setItem(Item item) { this.item = item; }
    public ClientInfo getClient() { return client; }
    public void setClient(ClientInfo client) { this.client = client; }
    public InsuranceInfo getInsurance() { return insurance; }
    public void setInsurance(InsuranceInfo insurance) { this.insurance = insurance; }
    public LocalDateTime getDealDate() { return dealDate; }
    public void setDealDate(LocalDateTime dealDate) { this.dealDate = dealDate; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
}