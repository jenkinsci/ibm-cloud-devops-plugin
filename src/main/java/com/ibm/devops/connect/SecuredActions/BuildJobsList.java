package com.ibm.devops.connect.SecuredAction;

import com.ibm.devops.connect.CloudItemListener;
import jenkins.model.Jenkins;
import hudson.model.AbstractItem;

import java.util.List;

public class BuildJobsList extends AbstractSecuredAction {

    protected void run(AbstractSecuredAction.ParamObj paramObj) {
        System.out.println("Running Operation As Another USER!!!!!!");

        List<AbstractItem> allProjects= Jenkins.getInstance().getAllItems(AbstractItem.class);

        CloudItemListener cil = new CloudItemListener();
        cil.buildJobsList();
    }
}