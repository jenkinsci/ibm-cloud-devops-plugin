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
import hudson.model.Describable;
import hudson.tasks.BuildStep;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;

import java.io.File;

import com.ibm.devops.connect.CloudCause.JobStatus;
import com.ibm.devops.connect.CloudCause;

import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;

/**
 * Jenkins server
 */

public class JenkinsJobStatus extends AbstractJenkinsStatus {

    public static final Logger log = LoggerFactory.getLogger(JenkinsJobStatus.class);

    public JenkinsJobStatus(AbstractBuild build, CloudCause cloudCause, BuildStep buildStep, BuildListener buildListener, Boolean newStep, Boolean isFatal) {
        this.run = build;
        this.cloudCause = cloudCause;
        this.buildStep = buildStep;
        this.newStep = newStep;
        this.isFatal = isFatal;
        this.taskListener = buildListener;
        this.isPaused = false;
        this.isPipeline = false;

        getEnvVars();
        getOrCreateCrAction();
    }

    protected FilePath getWorkspaceFilePath() {
        return ((AbstractBuild)run).getWorkspace();
    }


    protected void evaluateBuildStep() {
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

                if (cloudCause.isCreatedByCR()) {
                    cloudCause.updateLastStep(((Describable)buildStep).getDescriptor().getDisplayName(), newStatus, message, isFatal);
                }
            }
        }
    }

    protected void evaluatePipelineStep() {
        // No Op
    }

}