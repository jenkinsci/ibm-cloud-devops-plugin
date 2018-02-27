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
import com.ibm.devops.connect.Status.JenkinsJobStatus;

@Extension
public class CloudBuildStepListener extends BuildStepListener {
	public static final Logger log = LoggerFactory.getLogger(CloudBuildStepListener.class);

    public void finished(AbstractBuild build, BuildStep bs, BuildListener listener, boolean canContinue) {

        CloudCause cloudCause = getCloudCause(build);
        if (cloudCause == null) {
            cloudCause = new CloudCause();
        }
        JenkinsJobStatus status = new JenkinsJobStatus(build, cloudCause, bs, listener, false, !canContinue);
        JSONObject statusUpdate = status.generate();
        CloudPublisher cloudPublisher = new CloudPublisher();
        cloudPublisher.uploadJobStatus(statusUpdate);
    }

    public void started(AbstractBuild build, BuildStep bs, BuildListener listener) {
        // We listen to jobs that are started by IBM Cloud only
        if(this.shouldListen(build)) {
            JenkinsJobStatus status = new JenkinsJobStatus(build, getCloudCause(build), bs, listener, true, false);
            JSONObject statusUpdate = status.generate();
            CloudPublisher cloudPublisher = new CloudPublisher();
            cloudPublisher.uploadJobStatus(statusUpdate);
        }
    }

    private boolean shouldListen(AbstractBuild build) {
        if(getCloudCause(build) == null) {
            return false;
        } else {
            return true;
        }
    }

    private CloudCause getCloudCause(AbstractBuild build) {
        List<Cause> causes = build.getCauses();

        for(Cause cause : causes) {
            if (cause instanceof CloudCause ) {
                return (CloudCause)cause;
            }
        }

        return null;
    }
}