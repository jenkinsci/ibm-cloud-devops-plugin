package com.ibm.devops.connect.Status;

import hudson.model.Run;
import hudson.model.AbstractItem;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;
import hudson.model.Action;
import hudson.model.Describable;
import hudson.EnvVars;
import hudson.plugins.git.util.BuildData;
import hudson.plugins.git.util.Build;
import hudson.tasks.BuildStep;
import hudson.FilePath;
import hudson.model.Result;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jenkins.model.Jenkins;
import org.jenkinsci.plugins.uniqueid.IdStore;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import com.ibm.devops.connect.DevOpsGlobalConfiguration;
import com.ibm.devops.connect.CloudCause.JobStatus;
import com.ibm.devops.connect.CloudCause;

import com.ibm.devops.dra.EvaluateGate;
import com.ibm.devops.dra.GatePublisherAction;

import net.sf.json.JSONObject;

import java.io.IOException;
import java.lang.InterruptedException;
import java.util.List;
import java.util.Map;

abstract class AbstractJenkinsStatus {
    public static final Logger log = LoggerFactory.getLogger(AbstractJenkinsStatus.class);
    // Run
    protected Run run;

    protected CloudCause cloudCause;

    protected BuildStep buildStep;
    protected FlowNode node;

    protected Boolean newStep;
    protected Boolean isFatal;

    protected TaskListener taskListener;

    protected EnvVars envVars;
    protected CrAction crAction;

    protected Boolean isPipeline;
    protected Boolean isPaused;

    protected void getOrCreateCrAction() {
        // Get CrAction
        List<Action> actions = run.getActions();
        for(Action action : actions) {
            if (action instanceof CrAction) {
                crAction = (CrAction)action;
            }
        }

        // If not, create crAction
        if (crAction == null) {
            crAction = new CrAction();
            run.addAction(crAction);
        }
    }

    protected void getEnvVars() {
        try {
            if(run != null && taskListener != null) {
                this.envVars = run.getEnvironment(taskListener);
            }
        } catch (IOException ioEx) {
            log.warn("IOException thrown while trying to retrieve EnvVars in constructor: " + ioEx);
        } catch (InterruptedException intEx) {
            log.warn("InterruptedException thrown while trying to retrieve EnvVars in constructor: " + intEx);
        }
    }

    public JSONObject generateErrorStatus(String errorMessage) {
        JSONObject result = new JSONObject();

        cloudCause.addStep("Error: " + errorMessage, JobStatus.failure.toString(), "Failed due to error", true);

        result.put("status", JobStatus.failure.toString());
        result.put("timestamp", System.currentTimeMillis());
        result.put("syncId", Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncId());
        result.put("steps", cloudCause.getStepsArray());
        result.put("returnProps", cloudCause.getReturnProps());

        if(run != null) {
            result.put("url", Jenkins.getInstance().getRootUrl() + run.getUrl());
            result.put("jobExternalId", getJobUniqueIdFromBuild());
            result.put("name", run.getDisplayName());
        } else {
            result.put("url", Jenkins.getInstance().getRootUrl());
            result.put("name", "Job Error");
        }

        return result;
    }

    private String getJobUniqueIdFromBuild() {
        AbstractItem project = run.getParent();

        String projectId;

        if (IdStore.getId(project) != null) {
            projectId = IdStore.getId(project);
        } else {
            IdStore.makeId(project);
            projectId = IdStore.getId(project);
        }

        return projectId;
    }

    protected void evaluateSourceData() {
        List<Action> actions = run.getActions();

        // Try to get from the crAction
        SourceData sd = crAction.getSourceData();
        if(sd != null) {
            cloudCause.setSourceData(sd);
        }

        if (envVars != null) {
            for(Action action : actions) {
                // If using Hudson Git Plugin
                if (action instanceof BuildData) {
                    Map<String,Build> branchMap = ((BuildData)action).getBuildsByBranchName();

                    for(String branchName : branchMap.keySet()) {
                        Build gitBuild = branchMap.get(branchName);

                        if (gitBuild.getBuildNumber() == run.getNumber()) {
                            SourceData sourceData = new SourceData(branchName, gitBuild.getSHA1().getName(), "GIT");
                            sourceData.populateCommitMessage(taskListener, envVars, getWorkspaceFilePath(), gitBuild);

                            cloudCause.setSourceData(sourceData);
                            crAction.setSourceData(sourceData);
                        }
                    }
                }
            }
        }
    }

    protected void evaluateDRAData() {
        DRAData data = cloudCause.getDRAData();

        List<Action> actions = run.getActions();
        if(data == null) {
            data = crAction.getDRAData();
            cloudCause.setDRAData(data);
        }

        if(data == null) {
            data = new DRAData();
        }

        if (Jenkins.getInstance().getPlugin("ibm-cloud-devops") != null) {

            //This block if for non-pipeline jobs to set additional data that we have access to
            if (this.buildStep != null && this.buildStep instanceof EvaluateGate) {

                EvaluateGate egs = (EvaluateGate)buildStep;

                String environment = egs.getEnvName();
                String applicationName = egs.getApplicationName();
                String orgName = egs.getOrgName();
                String toolchainName = egs.getToolchainName();

                data.setApplicationName(applicationName);
                data.setOrgName(orgName);
                data.setToolchainName(toolchainName);
                data.setEnvironment(environment);
            }

            for(Action action : actions) {
                if (action instanceof GatePublisherAction) {
                    GatePublisherAction gpa = (GatePublisherAction)action;

                    String gateText = gpa.getText();
                    String riskDashboardLink = gpa.getRiskDashboardLink();
                    String decision = gpa.getDecision();
                    String policy = gpa.getPolicyName();

                    data.setGateText(gateText);
                    data.setDecision(decision);
                    data.setRiskDahboardLink(riskDashboardLink);
                    data.setPolicy(policy);
                    data.setBuildNumber(Integer.toString(run.getNumber()));

                    crAction.setDRAData(data);
                    cloudCause.setDRAData(data);
                }
            }
        }
    }

    private void evaluateEnvironment() {
        if( envVars != null ) {
            crAction.updateEnvProperties(envVars);
        }
    }

    public JSONObject generate() {
        JSONObject result = new JSONObject();

        evaluateSourceData();
        evaluateDRAData();
        evaluateEnvironment();

        if(isPipeline) {
            evaluatePipelineStep();
        } else {
            evaluateBuildStep();
        }

        if (run.getResult() == null) {
            if(run.isBuilding()) {
                result.put("status", JobStatus.started.toString());
            } else {
                result.put("status", JobStatus.unstarted.toString());
            }
        } else {
            if(run.getResult() == Result.SUCCESS) {
                result.put("status", JobStatus.success.toString());
            } else {
                result.put("status", JobStatus.failure.toString());
            }
        }

        result.put("timestamp", System.currentTimeMillis());
        result.put("syncId", Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncId());
        result.put("name", run.getDisplayName());
        result.put("steps", cloudCause.getStepsArray());
        result.put("url", Jenkins.getInstance().getRootUrl() + run.getUrl());
        result.put("returnProps", cloudCause.getReturnProps());
        result.put("isPipeline", isPipeline);
        result.put("isPaused", isPaused);
        result.put("jobName", run.getParent().getName());
        result.put("jobExternalId", getJobUniqueIdFromBuild());
        result.put("sourceData", cloudCause.getSourceDataJson());
        result.put("draData", cloudCause.getDRADataJson());
        result.put("crProperties", crAction.getCrProperties());
        result.put("envProperties", crAction.getEnvProperties());

        return result;
    }

    abstract protected FilePath getWorkspaceFilePath();

    abstract protected void evaluatePipelineStep();

    abstract protected void evaluateBuildStep();

}