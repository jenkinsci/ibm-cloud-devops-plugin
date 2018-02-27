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
import hudson.model.ParametersDefinitionProperty;
import hudson.model.queue.SubTask;
import hudson.model.ChoiceParameterDefinition;
import hudson.model.PasswordParameterDefinition;
import com.cloudbees.plugins.credentials.CredentialsParameterDefinition;
import hudson.model.BooleanParameterDefinition;

import java.util.Collection;

import net.sf.json.JSONObject;
import net.sf.json.JSONArray;

import org.apache.commons.lang.builder.ToStringBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jenkinsci.plugins.uniqueid.IdStore;

import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;

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
		jobToJson.put("displayName", displayName);
		jobToJson.put("name", name);
		jobToJson.put("fullName", fullName);
		jobToJson.put("jobUrl", jobUrl);
		jobToJson.put("jenkinsClass", this.item.getClass());

		String jobId;

		if(IdStore.getId(this.item) != null) {
			jobId = IdStore.getId(this.item);
		} else {
			IdStore.makeId(this.item);
			jobId = IdStore.getId(this.item);
		}

		jobToJson.put("id", jobId);
		jobToJson.put("instanceType", "JENKINS");
		//jobToJson.put("instanceName", Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getInstanceName());

		if(this.item instanceof WorkflowJob) {
			jobToJson.put("isPipeline", true);
			// TODO: Find a way to get Stage definitions
		} else {
			jobToJson.put("isPipeline", false);
		}

		jobToJson.put("params", getJobParams());

		return jobToJson;
	}

	private JSONArray getJobParams() {
		JSONArray result = new JSONArray();

		if (this.item instanceof WorkflowJob) {
			ParametersDefinitionProperty paramDefProperty = ((WorkflowJob)this.item).getProperty(ParametersDefinitionProperty.class);
			if (paramDefProperty != null) {
				List<ParameterDefinition> paramDefs = paramDefProperty.getParameterDefinitions();
				for (ParameterDefinition paramDef : paramDefs) {
					result.add(convertJobParameter(paramDef));
				}
			}
		} else if (this.item instanceof AbstractProject) {
			List<Action> actions = ((AbstractProject)this.item).getActions();

			for(Action action : actions) {
				if (action instanceof ParametersDefinitionProperty) {
					List<ParameterDefinition> paraDefs = ((ParametersDefinitionProperty)action).getParameterDefinitions();
					for (ParameterDefinition paramDef : paraDefs) {
						result.add(convertJobParameter(paramDef));
					}
					break;
				}
			}
		}

		return result;
	}

	private JSONObject convertJobParameter(ParameterDefinition paramDef) {
		JSONObject result = new JSONObject();
		result.put("name", paramDef.getName());
		result.put("type", paramDef.getType());
		result.put("description", paramDef.getDescription());
		ParameterValue pValue = paramDef.getDefaultParameterValue();
		if (pValue != null) {
			result.put("defaultValue", pValue.getValue());
		}

		if(paramDef instanceof ChoiceParameterDefinition) {
			List<String> options = ((ChoiceParameterDefinition)paramDef).getChoices();
			result.put("options", options);
		}

		return result;
	}
}
