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

import com.ibm.devops.dra.EvaluateGate;
import com.ibm.devops.dra.GatePublisherAction;

import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import java.util.Map;
import java.util.List;

/**
 * Jenkins server
 */

public class JenkinsPipelineStatus {

    private WorkflowRun workflowRun;
    private CloudCause cloudCause;
    private FlowNode node;
    private Boolean newStep;
    private Boolean isPaused;

    public JenkinsPipelineStatus(WorkflowRun workflowRun, CloudCause cloudCause, FlowNode node, boolean newStep, boolean isPaused) {
        this.workflowRun = workflowRun;
        this.cloudCause = cloudCause;
        this.node = node;
        this.newStep = newStep;
        this.isPaused = isPaused;
    }

    public JSONObject generate() {
        JSONObject result = new JSONObject();

        evaluateSourceData(workflowRun, cloudCause);
        evaluateDRAData();

        if(newStep && node == null) {
            cloudCause.addStep("Starting Jenkins Pipeline", JobStatus.success.toString(), "Successfully started pipeline...", false);
        } else if(newStep && node != null) {
            cloudCause.addStep(node.getDisplayName(), JobStatus.started.toString(), "Started stage", false);
        } else if (isPaused && node != null) {
            cloudCause.addStep(node.getDisplayName(), JobStatus.started.toString(), "Please acknowledge the Jenkins Pipeline input", false);
        } else if(!newStep && node != null) {

            if(node.getError() == null) {
                cloudCause.updateLastStep(null, JobStatus.success.toString(), "Stage is successful", false);
            } else {
                cloudCause.updateLastStep(null, JobStatus.failure.toString(), node.getError().getDisplayName(), false);
            }
        }

        if (workflowRun.getResult() == null) {
            if(workflowRun.isBuilding()) {
                result.put("status", JobStatus.started.toString());
            } else {
                result.put("status", JobStatus.unstarted.toString());
            }
        } else {
            if(workflowRun.getResult() == Result.SUCCESS) {
                result.put("status", JobStatus.success.toString());
            } else {
                result.put("status", JobStatus.failure.toString());
            }
        }

        result.put("timestamp", System.currentTimeMillis());
        result.put("syncId", Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncId());
        result.put("name", workflowRun.getDisplayName());
        result.put("steps", cloudCause.getStepsArray());
        result.put("url", Jenkins.getInstance().getRootUrl() + workflowRun.getUrl());
        result.put("returnProps", cloudCause.getReturnProps());
        result.put("isPipeline", true);
        result.put("isPaused", isPaused);

        //TODO
        //result.put("jobExternalId", getJobUniqueIdFromBuild(build));

        // AbstractProject project = (AbstractProject)build.getProject();
        // String jobName = project.getName();
        // result.put("jobName", jobName);

        result.put("sourceData", cloudCause.getSourceDataJson());
        result.put("draData", cloudCause.getDRADataJson());

        return result;
    }

    public JSONObject generateErrorStatus(String errorMessage) {

        return null;
    }

    private String getJobUniqueIdFromBuild(AbstractBuild build) {

        return null;
    }

    private void evaluateSourceData(WorkflowRun workflowRun, CloudCause cause) {
        List<Action> actions = workflowRun.getActions();

        for(Action action : actions) {
            // If using Hudson Git Plugin
            if (action instanceof BuildData) {
                Map<String,Build> branchMap = ((BuildData)action).getBuildsByBranchName();

                for(String branchName : branchMap.keySet()) {
                    Build gitBuild = branchMap.get(branchName);

                    if (gitBuild.getBuildNumber() == workflowRun.getNumber()) {
                        SourceData sourceData = new SourceData(branchName, gitBuild.getSHA1().getName(), "GIT");
                        cause.setSourceData(sourceData);
                    }
                }
            }
        }
    }

    private void evaluateDRAData() {
        DRAData data = cloudCause.getDRAData();

        List<Action> actions = workflowRun.getActions();
        if(data == null) {
            for(Action action : actions) {
                if (action instanceof CrDraAction) {
                    CrDraAction cda = (CrDraAction)action;
                    data = cda.getDRAData();
                    cloudCause.setDRAData(data);
                }
            }
        }

        if(data == null) {
            // CAN NOT GET THIS DATA FROM PIPELINE
            // data.setApplicationName(applicationName);
            // data.setOrgName(orgName);
            // data.setToolchainName(toolchainName);
            // data.setEnvironment(environment);

            for(Action action : actions) {
                if (action instanceof GatePublisherAction) {
                    data = new DRAData();
                    GatePublisherAction gpa = (GatePublisherAction)action;

                    String gateText = gpa.getText();
                    String riskDashboardLink = gpa.getRiskDashboardLink();
                    String decision = gpa.getDecision();
                    String policy = gpa.getPolicyName();

                    data.setGateText(gateText);
                    data.setDecision(decision);
                    data.setRiskDahboardLink(riskDashboardLink);
                    data.setPolicy(policy);
                    data.setBuildNumber(Integer.toString(workflowRun.getNumber()));

                    CrDraAction cda = new CrDraAction(data);
                    workflowRun.addAction(cda);

                    cloudCause.setDRAData(data);
                }
            }

        }
    }
}