package com.ibm.devops.connect;

import hudson.model.Cause;
import hudson.model.Node;
import com.ibm.cloud.urbancode.connect.client.ConnectSocket;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import net.sf.json.JSONObject;
import net.sf.json.JSONArray;

public class CloudCause extends Cause {

    public enum JobStatus {
        unstarted, started, success, failure
    }

    private String workId;
    private JSONObject returnProps;
    private List<JSONObject> steps = new ArrayList<JSONObject>();

    private SourceData sourceData;

    // private ConnectSocket socket;

    public CloudCause(ConnectSocket socket, String workId, JSONObject returnProps) {
        this.workId = workId;
        this.returnProps = returnProps;
        // this.socket = socket;
    }

    @Override
    public String getShortDescription() {
        return "Started due to a request from IBM Continuous Release. Work Id: " + this.workId;
    }

    public void addStep(String name, String status, String message, boolean isFatal) {
        JSONObject obj = new JSONObject();
        obj.put("name", name);
        obj.put("status", status);
        obj.put("message", message);
        obj.put("isFatal", isFatal);
        steps.add(obj);
    }

    public void setSourceData(SourceData sourceData) {
        this.sourceData = sourceData;
    }

    public SourceData getSourceData() {
        return this.sourceData;
    }

    public JSONObject getSourceDataJson() {
        if(this.sourceData == null) {
            return new JSONObject();
        } else {
            return sourceData.toJson();
        }
    }

    public void updateLastStep(String name, String status, String message, boolean isFatal) {
        JSONObject obj = steps.get(steps.size() - 1);
        obj.put("name", name);
        obj.put("status", status);
        obj.put("message", message);
        obj.put("isFatal", isFatal);
    }

    public void addSourceData(String branch, String revision, String scmName, Set<String> remoteUrls) {

    }

    public JSONObject getReturnProps() {
        return returnProps;
    }

    public JSONArray getStepsArray() {
        JSONArray result = new JSONArray();
        for(JSONObject obj : steps) {
            result.add(obj);
        }

        return result;
    }
}