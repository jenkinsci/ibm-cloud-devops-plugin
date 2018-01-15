/*
 <notice>

 Copyright 2016, 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.connect;


import hudson.EnvVars;
import hudson.Extension;
import hudson.model.*;
import hudson.model.listeners.ItemListener;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.builder.ToStringBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.json.JSONObject;

import com.cloudbees.hudson.plugins.folder.Folder;

@Extension
public class CloudItemListener extends ItemListener {
	public static final Logger log = LoggerFactory.getLogger(CloudItemListener.class);
	private String logPrefix= "[IBM Cloud DevOps] CloudItemListener#";

    public CloudItemListener(){
    	logPrefix= logPrefix + "CloudItemListener ";
    	log.info(logPrefix + "CloudItemListener started...");
    }

    @Override
    public void onCreated(Item item) {
        handleEvent(item, "CREATED");
    }

    @Override
    public void onDeleted(Item item) {
        handleEvent(item, "DELETED");
    }

    @Override
    public void onUpdated(Item item) {
        handleEvent(item, "UPDATED");
    }

    private void handleEvent(Item item, String phase) {
        CloudSocketComponent socket = new ConnectComputerListener().getCloudSocketInstance();
        if(socket.connected()) {
            if( !(item instanceof Folder) ) {
                JenkinsJob jenkinsJob= new JenkinsJob(item);
                log.info(ToStringBuilder.reflectionToString(jenkinsJob.toJson()) + " was " + phase);
                CloudPublisher cloudPublisher = new CloudPublisher();
                cloudPublisher.uploadJobInfo(jenkinsJob.toJson());
            }
        }

    	// we'll handle the updates to the sync app here
    }

    public List<JSONObject> buildJobsList() {
    	log.info(logPrefix + "Building the list of Jenkins jobs...");
    	List<Item> allProjects= JenkinsServer.getAllItems();
    	List<JSONObject> allJobs = new ArrayList<JSONObject>();

        CloudPublisher cloudPublisher = new CloudPublisher();
    	for (Item anItem : allProjects) {
            if( !(anItem instanceof Folder) ) {
                JenkinsJob jenkinsJob= new JenkinsJob(anItem);
                allJobs.add(jenkinsJob.toJson());

                cloudPublisher.uploadJobInfo(jenkinsJob.toJson());
            }
		}
    	return allJobs;
    }
}