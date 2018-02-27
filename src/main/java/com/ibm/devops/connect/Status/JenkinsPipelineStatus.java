/*
 <notice>

 Copyright 2018 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.connect.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import hudson.model.TaskListener;
import hudson.FilePath;

import java.io.File;

import com.ibm.devops.connect.CloudCause.JobStatus;
import com.ibm.devops.connect.CloudCause;

import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;

import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;

/**
 * Jenkins server
 */

public class JenkinsPipelineStatus extends AbstractJenkinsStatus {

    private boolean shouldCheckSCM = false;

    public static final Logger log = LoggerFactory.getLogger(JenkinsPipelineStatus.class);

    public JenkinsPipelineStatus(WorkflowRun workflowRun, CloudCause cloudCause, FlowNode node, TaskListener listener, boolean newStep, boolean isPaused) {
        this.run = workflowRun;
        this.cloudCause = cloudCause;
        this.node = node;
        this.newStep = newStep;
        this.isPaused = isPaused;
        this.isPipeline = true;
        this.taskListener = listener;

        getEnvVars();
        getOrCreateCrAction();

        if (envVars != null) {
            shouldCheckSCM = true;
        }
    }

    protected FilePath getWorkspaceFilePath() {
        FlowExecution exec = ((WorkflowRun)run).getExecution();
        if(exec == null)
            return null;

        FlowGraphWalker w = new FlowGraphWalker(exec);
        for (FlowNode n : w) {
            if (n.getClass().getName().equals("org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode")) {
                WorkspaceAction action = n.getAction(WorkspaceAction.class);
                if(action != null) {
                    String node = action.getNode().toString();
                    String workspace = action.getPath().toString();
                    FilePath result = new FilePath(new File(workspace));

                    return result;
                }
            }
        }

        return null;
    }

    protected void evaluatePipelineStep() {
        if(newStep && node == null) {
            cloudCause.addStep("Starting Jenkins Pipeline", JobStatus.success.toString(), "Successfully started pipeline...", false);
        } else if(newStep && node != null) {
            cloudCause.addStep(node.getDisplayName(), JobStatus.started.toString(), "Started stage", false);
        } else if (isPaused && node != null) {
            cloudCause.addStep(node.getDisplayName(), JobStatus.started.toString(), "Please acknowledge the Jenkins Pipeline input", false);
        } else if(!newStep && node != null) {

            if(node.getError() == null) {
                if(cloudCause.isCreatedByCR()) {
                    cloudCause.updateLastStep(null, JobStatus.success.toString(), "Stage is successful", false);
                } else {
                    cloudCause.addStep(null, JobStatus.success.toString(), "Stage is successful", false);
                }
            } else {
                cloudCause.updateLastStep(null, JobStatus.failure.toString(), node.getError().getDisplayName(), false);
            }
        }
    }

    protected void evaluateBuildStep() {
        // No Op
    }
}