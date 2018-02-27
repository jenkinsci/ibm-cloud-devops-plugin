package com.ibm.devops.connect.Status;

import net.sf.json.JSONObject;
import java.util.Set;

public class DRAData {

    private String applicationName;
    private String orgName;
    private String toolchainName;
    private String environment;
    private String buildNumber;
    private String policy;
    private String gateText;
    private String decision;
    private String riskDahboardLink;


    private String type;
    private Set<String> remoteUrls;

    public DRAData() {
    }

    public void setApplicationName (String applicationName) {
        this.applicationName = applicationName;
    }

    public void setOrgName (String orgName) {
        this.orgName = orgName;
    }

    public void setToolchainName(String toolchainName) {
        this.toolchainName = toolchainName;
    }

    public void setEnvironment (String environment) {
        this.environment = environment;
    }

    public void setBuildNumber (String buildNumber) {
        this.buildNumber = buildNumber;
    }

    public void setPolicy (String policy) {
        this.policy = policy;
    }

    public void setGateText (String gateText) {
        this.gateText = gateText;
    }

    public void setDecision (String decision) {
        this.decision = decision;
    }

    public void setRiskDahboardLink (String riskDahboardLink) {
        this.riskDahboardLink = riskDahboardLink;
    }

    public JSONObject toJson() {
        JSONObject result = new JSONObject();

        result.put("applicationName", applicationName);
        result.put("orgName", applicationName);
        result.put("toolchainName", toolchainName);
        result.put("environment", environment);
        result.put("buildNumber", buildNumber);
        result.put("policy", policy);
        result.put("gateText", gateText);
        result.put("decision", decision);
        result.put("riskDahboardLink", riskDahboardLink);

        return result;
    }
}