package com.ibm.devops.connect;

import net.sf.json.JSONObject;
import java.util.Set;

public class SourceData {
    
    private String branch;
    private String revision;
    private String scmName;
    private String type;
    private Set<String> remoteUrls;

    public SourceData(String branch, String revision, String type) {
        this.branch = branch;
        this.revision = revision;
        this.type = type;
    }

    public void setBranch (String branch) {
        this.branch = branch;
    }

    public void setRevision (String revision) {
        this.revision = revision;
    }

    public void setScmName(String scmName) {
        this.scmName = scmName;
    }

    public void setType (String type) {
        this.type = type;
    }

    public void setRemoteUrls (Set<String> remoteUrls) {
        this.remoteUrls = remoteUrls;
    }

    public JSONObject toJson() {
        JSONObject result = new JSONObject();

        result.put("branch", branch);
        result.put("revision", revision);
        result.put("scmName", scmName);
        result.put("type", type);
        if(remoteUrls != null) {
            result.put("remoteUrls", remoteUrls.toArray());
        }

        return result;
    }
}