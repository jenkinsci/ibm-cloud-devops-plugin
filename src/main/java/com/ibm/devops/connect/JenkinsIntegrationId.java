package com.ibm.devops.connect;

import org.jenkinsci.plugins.uniqueid.IdStore;
import com.ibm.devops.connect.DevOpsGlobalConfiguration;
import jenkins.model.Jenkins;

public class JenkinsIntegrationId {
    public JenkinsIntegrationId () {

    }

    public String getIntegrationId() {
        String result = getSyncId() + "_" + getJenkinsId();
        return result;
    }

    private String getJenkinsId() {
        String jenkinsId;
        if (IdStore.getId(Jenkins.getInstance()) != null) {
            jenkinsId = IdStore.getId(Jenkins.getInstance());
        } else {
            IdStore.makeId(Jenkins.getInstance());
            jenkinsId = IdStore.getId(Jenkins.getInstance());
        }

        return jenkinsId;
    }

    private String getSyncId() {
        return Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncId();
    }
}