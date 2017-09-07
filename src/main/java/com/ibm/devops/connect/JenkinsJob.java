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

import net.sf.json.JSONObject;

import org.apache.commons.lang.builder.ToStringBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jenkinsci.plugins.uniqueid.IdStore;

/**
 * Jenkins server
 */

public class JenkinsJob {
	final private Item item;
	public static final Logger log = LoggerFactory.getLogger(JenkinsJob.class);
	
	public JenkinsJob (Item item) {
		this.item= item;
	}
	// TODO: see what this guy can do for us:
	// - start: start a job
	// - getStatus: get the status of a job
	// - get build history
	// - stop / cancel
	// - other stuff?
	public JSONObject toJson() {
		String displayName= this.item.getDisplayName();
		String name= this.item.getName();
		String fullName= this.item.getFullName();
		String jobUrl= this.item.getUrl();
		
		JSONObject jobToJson = new JSONObject();
		jobToJson.put("display_name", this.item.getDisplayName());
		jobToJson.put("name", this.item.getName());
		jobToJson.put("full_name", this.item.getFullName());
		jobToJson.put("job_url", this.item.getUrl());

		String jobId;

		if(IdStore.getId(this.item) != null) {
			jobId = IdStore.getId(this.item);
		} else {
			IdStore.makeId(this.item);
			jobId = IdStore.getId(this.item);
		}

		jobToJson.put("id", jobId);
		jobToJson.put("instance_type", "JENKINS");

    	// log.info("job: " + ToStringBuilder.reflectionToString(jobToJson));    	
		return jobToJson;
	}
}
