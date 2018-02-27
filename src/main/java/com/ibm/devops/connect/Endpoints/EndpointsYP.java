package com.ibm.devops.connect.Endpoints;

public class EndpointsYP implements IEndpoints {
    private static final String SYNC_API_ENPOINT = "https://ucreporting-sync-api.mybluemix.net/";
    private static final String SYNC_STORE_ENPOINT = "https://uccloud-sync-store.mybluemix.net/";
    private static final String CONNECT_ENPOINT = "https://uccloud-connect.mybluemix.net";

    public String getSyncApiEndpoint() {
        return SYNC_API_ENPOINT;
    }

    public String getSyncStoreEndpoint() {
        return SYNC_STORE_ENPOINT;
    }

    public String getConnectEndpoint() {
        return CONNECT_ENPOINT;
    }
}