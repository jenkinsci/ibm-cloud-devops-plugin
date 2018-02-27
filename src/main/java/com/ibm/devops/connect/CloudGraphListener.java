/*
 <notice>

 Copyright 2016, 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.connect;

import java.util.Map;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.*;
import hudson.model.BuildStepListener;
import hudson.tasks.BuildStep;
import hudson.model.AbstractBuild;
import hudson.tasks.Builder;
import hudson.model.Result;

import jenkins.model.Jenkins;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

import org.apache.commons.lang.builder.ToStringBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.json.JSONObject;
import net.sf.json.JSONArray;

import com.ibm.devops.connect.CloudCause.JobStatus;

import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.actions.PauseAction;

import com.ibm.devops.connect.Status.JenkinsPipelineStatus;

import java.io.IOException;

@Extension
public class CloudGraphListener implements GraphListener {
	public static final Logger log = LoggerFactory.getLogger(CloudGraphListener.class);

    public void onNewHead(FlowNode node) {
        FlowExecution execution = node.getExecution();

        WorkflowRun workflowRun = null;

        try {
            if (execution.getOwner().getExecutable() instanceof WorkflowRun) {
                workflowRun = (WorkflowRun)(execution.getOwner().getExecutable());
            }
        } catch (IOException e) {
            log.error("HIT THE IOEXCEPTION: " + e);
            return;
        }

        CloudCause cloudCause = getCloudCause(workflowRun);
        if (cloudCause == null) {
            cloudCause = new CloudCause();
        }

        boolean isStartNode = node.getClass().getName().equals("org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode");
        boolean isEndNode = node.getClass().getName().equals("org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode");
        boolean isPauseNode = PauseAction.isPaused(node);

        if(isStartNode || isEndNode || isPauseNode) {
            JenkinsPipelineStatus status = new JenkinsPipelineStatus(workflowRun, cloudCause, node, null, isStartNode, isPauseNode);
            JSONObject statusUpdate = status.generate();
            CloudPublisher cloudPublisher = new CloudPublisher();
            cloudPublisher.uploadJobStatus(statusUpdate);
        }
    }

    private CloudCause getCloudCause(WorkflowRun workflowRun) {
        List<Cause> causes = workflowRun.getCauses();

        for(Cause cause : causes) {
            if (cause instanceof CloudCause ) {
                return (CloudCause)cause;
            }
        }

        return null;
    }
}