package com.ibm.devops.connect;

import hudson.model.Action;

public class CrDraAction implements Action {

    private DRAData draData;

    public CrDraAction(DRAData data) {
        this.draData = data;
    }

    public DRAData getDRAData() {
        return draData;
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
