/*
 <notice>

 Copyright 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.connect;

import hudson.model.*;
import hudson.model.Item;
import hudson.tasks.BuildStep;

import net.sf.json.JSONObject;

import org.apache.commons.lang.builder.ToStringBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jenkinsci.plugins.uniqueid.IdStore;

import jenkins.model.Jenkins;

import com.ibm.devops.dra.DevOpsGlobalConfiguration;
import com.ibm.devops.connect.CloudCause.JobStatus;

import org.jenkinsci.plugins.uniqueid.IdStore;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.git.util.Build;

import java.util.Map;
import java.util.List;

/**
 * Jenkins server
 */

public class JenkinsJobStatus {

    private AbstractBuild build;
    private CloudCause cloudCause;
    private BuildStep buildStep;
    private Boolean newStep;
    private Boolean isFatal;

    public JenkinsJobStatus(AbstractBuild build, CloudCause cloudCause, BuildStep buildStep, Boolean newStep, Boolean isFatal) {
        this.build = build;
        this.cloudCause = cloudCause;
        this.buildStep = buildStep;
        this.newStep = newStep;
        this.isFatal = isFatal;
    }

    public JSONObject generate() {
        JSONObject result = new JSONObject();
        
        evaluateSourceData(build, cloudCause);

        if(!(buildStep instanceof hudson.model.ParametersDefinitionProperty)) {
            if (newStep) {
                cloudCause.addStep(((Describable)buildStep).getDescriptor().getDisplayName(), JobStatus.started.toString(), "Started a build step", false);
            } else {
                String newStatus;
                String message;
                if (!isFatal) {
                    newStatus = JobStatus.success.toString();
                    message = "The build step finished and the job will continue.";
                } else {
                    newStatus = JobStatus.failure.toString();
                    message = "The build step failed and the job can not continue.";
                }

                cloudCause.updateLastStep(((Describable)buildStep).getDescriptor().getDisplayName(), newStatus, message, isFatal);
            }
        }

        // TODO: Premature success is causing successful results when job actually fails
        // System.out.println("\t\tRESULT \t IS BUILDING \t hasntStartedYet \t isCompleteBuild");
        // System.out.println("\t\t" + build.getResult() + "\t\t" + build.isBuilding() + "\t\t" + build.hasntStartedYet() + "\t\t" + (build.getResult() == null ? "IT was NULL" : build.getResult().isCompleteBuild()));
        
        if (build.getResult() == null) {
            if(build.isBuilding()) {
                result.put("status", JobStatus.started.toString());
            } else {
                result.put("status", JobStatus.unstarted.toString());
            }
        } else {
            if(build.getResult() == Result.SUCCESS) {
                result.put("status", JobStatus.success.toString());
            } else {
                result.put("status", JobStatus.failure.toString());
            }
        }

        result.put("timestamp", System.currentTimeMillis());
        result.put("syncId", Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncId());
        result.put("name", build.getDisplayName());
        result.put("steps", cloudCause.getStepsArray());
        result.put("url", Jenkins.getInstance().getRootUrl() + build.getUrl());
        result.put("returnProps", cloudCause.getReturnProps());

        result.put("jobExternalId", getJobUniqueIdFromBuild(build));

        result.put("sourceData", cloudCause.getSourceDataJson());

        return result;
    }

    private String getJobUniqueIdFromBuild(AbstractBuild build) {
        AbstractProject project = (AbstractProject)build.getProject();

        String jenkinsId;

        if (IdStore.getId(Jenkins.getInstance()) != null) {
            jenkinsId = IdStore.getId(Jenkins.getInstance());
        } else {
            IdStore.makeId(Jenkins.getInstance());
            jenkinsId = IdStore.getId(Jenkins.getInstance());
        }

        return jenkinsId;
    }

    private void evaluateSourceData(AbstractBuild build, CloudCause cause) {
        List<Action> actions = build.getActions();

        for(Action action : actions) {
            // If using Hudson Git Plugin
            if (action instanceof BuildData) {
                Map<String,Build> branchMap = ((BuildData)action).getBuildsByBranchName();

                for(String branchName : branchMap.keySet()) {
                    Build gitBuild = branchMap.get(branchName);

                    if (gitBuild.getBuildNumber() == build.getNumber()) {
                        SourceData sourceData = new SourceData(branchName, gitBuild.getSHA1().getName(), "GIT");
                        cause.setSourceData(sourceData);
                    }
                }
            }
        }
    }

}