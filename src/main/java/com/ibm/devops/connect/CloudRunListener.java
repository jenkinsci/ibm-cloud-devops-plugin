/*
 <notice>

 Copyright 2018 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
*/

package com.ibm.devops.connect;

import hudson.Extension;
import hudson.model.listeners.RunListener;
import hudson.model.TaskListener;
import hudson.model.Cause;

import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.json.JSONObject;
import net.sf.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

import com.ibm.devops.connect.CloudCause.JobStatus;
import com.ibm.devops.connect.Status.JenkinsPipelineStatus;

@Extension
public class CloudRunListener extends RunListener<WorkflowRun> {
    public static final Logger log = LoggerFactory.getLogger(CloudRunListener.class);

    @Override
    public void onStarted(WorkflowRun workflowRun, TaskListener listener) {
        // http://javadoc.jenkins.io/plugin/workflow-job/org/jenkinsci/plugins/workflow/job/WorkflowRun.html
        CloudCause cloudCause = getCloudCause(workflowRun);
        if (cloudCause == null) {
            cloudCause = new CloudCause();
        }
        JenkinsPipelineStatus status = new JenkinsPipelineStatus(workflowRun, cloudCause, null, listener, true, false);
        JSONObject statusUpdate = status.generate();
        CloudPublisher cloudPublisher = new CloudPublisher();
        cloudPublisher.uploadJobStatus(statusUpdate);
    }

    @Override
    public void onCompleted(WorkflowRun workflowRun, TaskListener listener) {
        CloudCause cloudCause = getCloudCause(workflowRun);
        if (cloudCause == null) {
            cloudCause = new CloudCause();
        }
        JenkinsPipelineStatus status = new JenkinsPipelineStatus(workflowRun, cloudCause, null, listener, false, false);
        JSONObject statusUpdate = status.generate();
        CloudPublisher cloudPublisher = new CloudPublisher();
        cloudPublisher.uploadJobStatus(statusUpdate);
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
