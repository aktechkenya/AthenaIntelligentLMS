package com.athena.lms.fraud.dto.response;

import lombok.Data;
import java.util.List;

@Data
public class NetworkNodeResponse {
    private String customerId;
    private String riskLevel;
    private int linkCount;
    private List<LinkResponse> links;

    @Data
    public static class LinkResponse {
        private String linkedCustomerId;
        private String linkType;
        private String linkValue;
        private int strength;
        private boolean flagged;
    }
}
