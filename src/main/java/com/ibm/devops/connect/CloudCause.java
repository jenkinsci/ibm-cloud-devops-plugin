package com.ibm.devops.connect;

import hudson.model.Cause;
import hudson.model.Node;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import net.sf.json.JSONObject;
import net.sf.json.JSONArray;
import com.ibm.devops.connect.Status.DRAData;
import com.ibm.devops.connect.Status.SourceData;

/**
* This is the cause object that is attached to a build if it is started by the IBM Cloud.
*/
public class CloudCause extends Cause {

    public enum JobStatus {
        unstarted, started, success, failure
    }

    private String workId;
    private JSONObject returnProps;
    private List<JSONObject> steps = new ArrayList<JSONObject>();

    private SourceData sourceData;
    private DRAData draData;

    private Boolean createdFromCR = false;

    // private ConnectSocket socket;

    public CloudCause(String workId, JSONObject returnProps) {
        this.workId = workId;
        this.returnProps = returnProps;
        this.createdFromCR = true;
    }

    public CloudCause() {
        this.createdFromCR = false;
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

    public void setDRAData(DRAData draData) {
        this.draData = draData;
    }

    public DRAData getDRAData() {
        return this.draData;
    }

    public JSONObject getSourceDataJson() {
        if(this.sourceData == null) {
            return new JSONObject();
        } else {
            return sourceData.toJson();
        }
    }

    public JSONObject getDRADataJson() {
        if(this.draData == null) {
            return new JSONObject();
        } else {
            return draData.toJson();
        }
    }

    public void updateLastStep(String name, String status, String message, boolean isFatal) {
        if (steps.size() == 0) {
            addStep(name, status, message, isFatal);
        } else {
            JSONObject obj = steps.get(steps.size() - 1);
            if(name != null) {
                obj.put("name", name);
            }
            obj.put("status", status);
            obj.put("message", message);
            obj.put("isFatal", isFatal);
        }
    }

    public Boolean isCreatedByCR() {
        return this.createdFromCR;
    }

    public JSONObject getReturnProps() {
        return returnProps;
    }

    public JSONArray getStepsArray() {
        JSONArray result = new JSONArray();
        for (JSONObject obj : steps) {
            result.add(obj);
        }

        return result;
    }
}