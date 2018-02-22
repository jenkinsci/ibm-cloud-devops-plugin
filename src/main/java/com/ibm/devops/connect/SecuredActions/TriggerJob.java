package com.ibm.devops.connect.SecuredAction;

import com.ibm.devops.connect.CloudWorkListener;
import jenkins.model.Jenkins;
import hudson.model.AbstractItem;


import com.ibm.cloud.urbancode.connect.client.ConnectSocket;

import java.util.List;

public class TriggerJob extends AbstractSecuredAction {

    protected void run(AbstractSecuredAction.ParamObj paramObj) {
        System.out.println("Running Operation As Another USER!!!!!!");

        TriggerJobParamObj triggerJobParamObj = (TriggerJobParamObj)paramObj;

        CloudWorkListener cwl = new CloudWorkListener();
        cwl.callSecured(triggerJobParamObj.socket, triggerJobParamObj.event, triggerJobParamObj.args);
    }

    public class TriggerJobParamObj extends AbstractSecuredAction.ParamObj {

        public ConnectSocket socket;
        public String event;
        public Object[] args;

        public TriggerJobParamObj(ConnectSocket socket, String event, Object... args) {
            this.socket = socket;
            this.event = event;
            this.args = args;
        }
    }
}