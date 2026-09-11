package com.raghunath.hotelview.entity.buyandsell;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "dealer_data_versions")
public class DealerDataVersion {

    @Id
    private String id;

    private String dealerId;
    private long clientVersion;
    private long itemVersion;
    private long completedDealVersion;
    private long insuranceVersion;
}