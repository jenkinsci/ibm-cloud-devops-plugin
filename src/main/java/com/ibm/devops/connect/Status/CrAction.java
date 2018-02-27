package com.ibm.devops.connect.Status;

import hudson.model.Action;
import java.util.Map;
import java.util.TreeMap;

public class CrAction implements Action {

    private DRAData draData;
    private SourceData sourceData;
    private Map<String, String> crProperties = new TreeMap<String, String>();
    private Map<String, String> envProperties = new TreeMap<String, String>();

    public CrAction() {
    }

    public DRAData getDRAData() {
        return draData;
    }

    public void setDRAData (DRAData draData) {
        this.draData = draData;
    }

    public SourceData getSourceData() {
        return sourceData;
    }

    public void setSourceData (SourceData sourceData) {
        this.sourceData = sourceData;
    }

    public void updateCrProperties (Map<String, String> properties) {
        this.crProperties.putAll(properties);
    }

    public Map<String, String> getCrProperties () {
        return crProperties;
    }

    public void updateEnvProperties (Map<String, String> properties) {
        this.envProperties.putAll(properties);
    }

    public Map<String, String> getEnvProperties () {
        return envProperties;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return null;
    }
}
