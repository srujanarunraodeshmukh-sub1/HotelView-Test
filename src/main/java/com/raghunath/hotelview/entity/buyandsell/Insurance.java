package com.raghunath.hotelview.entity.buyandsell;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "insurances")
public class Insurance {

    @Id
    private String id;

    private String dealerId;
    private String clientId;

    private ClientInfo client;
    private InsuranceInfo insurance;

    private LocalDateTime createdAt;
    private long version;
    private boolean deleted;

    public static class ClientInfo{
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

    public static class InsuranceInfo{
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

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDealerId() { return dealerId; }
    public void setDealerId(String dealerId) { this.dealerId = dealerId; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public ClientInfo getClient() { return client; }
    public void setClient(ClientInfo client) { this.client = client; }
    public InsuranceInfo getInsurance() { return insurance; }
    public void setInsurance(InsuranceInfo insurance) { this.insurance = insurance; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
}
