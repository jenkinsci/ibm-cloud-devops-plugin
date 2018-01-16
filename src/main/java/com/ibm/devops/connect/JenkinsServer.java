/*
 <notice>

 Copyright 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.connect;

import com.cloudbees.hudson.plugins.folder.Folder;

import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.common.*;
import com.cloudbees.plugins.credentials.domains.*;
import com.cloudbees.plugins.credentials.impl.*;

import hudson.model.*;
import hudson.model.Item;
import hudson.model.TopLevelItem;

import hudson.security.AuthorizationStrategy;
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import hudson.security.SecurityRealm;

import java.io.ByteArrayInputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

import jenkins.model.Jenkins;

import net.sf.json.*;

import org.apache.commons.lang.builder.ToStringBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jenkins server
 */

public class JenkinsServer {
	public static final Logger log = LoggerFactory.getLogger(JenkinsServer.class);
    private static String logPrefix= "[IBM Cloud DevOps] JenkinsServer#";

    // creds
    private static String BLX_CREDS= "IBM_CLOUD_DEVOPS_CREDS_API";
    private static String BLX_CREDS_DESC= "IBM DevOps Bluemix credentials";
    // folder and job
    private static String FOLDER_SPEC= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><com.cloudbees.hudson.plugins.folder.Folder plugin=\"cloudbees-folder\"><description>Folder created by the IBM Devops plugin</description></com.cloudbees.hudson.plugins.folder.Folder>";
    private static String jobSrc= "<?xml version='1.0' encoding='UTF-8'?>\r\n<flow-definition plugin=\"workflow-job@2.10\">\r\n    <description></description>\r\n    <keepDependencies>false</keepDependencies>\r\n    <properties>\r\n        <org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty>\r\n            <triggers>\r\n                <hudson.triggers.SCMTrigger>\r\n                    <spec>* * * * *</spec>\r\n                    <ignorePostCommitHooks>false</ignorePostCommitHooks>\r\n                </hudson.triggers.SCMTrigger>\r\n            </triggers>\r\n        </org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty>\r\n    </properties>\r\n    <definition class=\"org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition\" plugin=\"workflow-cps@2.30\">\r\n        <scm class=\"hudson.plugins.git.GitSCM\" plugin=\"git@3.3.0\">\r\n            <configVersion>2</configVersion>\r\n            <userRemoteConfigs>\r\n                <hudson.plugins.git.UserRemoteConfig>\r\n                    <url>https://github.com/ejodet/discovery-nodejs</url>\r\n                </hudson.plugins.git.UserRemoteConfig>\r\n            </userRemoteConfigs>\r\n            <branches>\r\n                <hudson.plugins.git.BranchSpec>\r\n                    <name>*/mastertoto</name>\r\n                </hudson.plugins.git.BranchSpec>\r\n            </branches>\r\n            <doGenerateSubmoduleConfigurations>false</doGenerateSubmoduleConfigurations>\r\n            <submoduleCfg class=\"list\"/>\r\n            <extensions/>\r\n        </scm>\r\n        <scriptPath>Jenkinsfile</scriptPath>\r\n        <lightweight>true</lightweight>\r\n    </definition>\r\n    <triggers/>\r\n</flow-definition>";

    public static Collection<String> getJobNames() {
    	log.debug(logPrefix + "getJobNames - get the list of job names");
    	Collection<String> allJobNames= Jenkins.getInstance().getJobNames();
    	log.debug(logPrefix + "getJobNames - retrieved " + allJobNames.size() + " JobNames");
    	for (Iterator iterator = allJobNames.iterator(); iterator.hasNext();) {

    		String aJobName = (String) iterator.next();
    		log.debug(logPrefix + "job: " + aJobName);
    	}
    	return Jenkins.getInstance().getJobNames();
    }

    public static List<Item> getAllItems() {
    	log.debug(logPrefix + "getAllItems - get the list of all items");
    	List<Item> allItems= Jenkins.getInstance().getAllItems();
    	if (allItems.size() == 0) { // ensure we're able to list all items
    		AuthorizationStrategy authorizationStrategy= Jenkins.getInstance().getAuthorizationStrategy();
    		if (authorizationStrategy instanceof FullControlOnceLoggedInAuthorizationStrategy) {
    			// allow anoymous read in order to get all items
        		FullControlOnceLoggedInAuthorizationStrategy strat= (FullControlOnceLoggedInAuthorizationStrategy) authorizationStrategy;
        		// remember previous settings
        		boolean isAllowAnonymousRead= strat.isAllowAnonymousRead();
        		strat.setAllowAnonymousRead(true);
        		allItems= Jenkins.getInstance().getAllItems();
        		strat.setAllowAnonymousRead(isAllowAnonymousRead);
        		Jenkins.getInstance().setAuthorizationStrategy(strat);
    		}
    	}
    	log.debug(logPrefix + "getAllItems - Retrieved " + allItems.size() + " projects");
    	return allItems;
    }

    public static Item getItemByName(String itemName) {
    	log.info(logPrefix + "Retrieving project " + itemName);
    	List<Item> allProjects= JenkinsServer.getAllItems();

    	for (Item anItem : allProjects) {
    		String aName = anItem.getFullName();
    		log.info(logPrefix + "project " + aName);
    		if (itemName.equals(aName)) {
    			log.info(logPrefix + "Project " + itemName + " retrieved!");
    			return anItem;
    		}
		}
    	log.info(logPrefix + "Project " + itemName + " not found!");
    	return null;
    }

    public static void createJob(JSONObject newJob) {
    	log.debug(logPrefix + "createJob - Creating a new job.");
    	if(validCreationRequest(newJob)) {
    		// get current security settings
    		SecurityRealm securityRealm= Jenkins.getInstance().getSecurityRealm();
    		AuthorizationStrategy authorizationStrategy= Jenkins.getInstance().getAuthorizationStrategy();

        	// temporarily disable security as we are not allowed to create jobs as anonymous
        	disableSecurity();
    		try {
    			// create creds if necessary
    			createCredentials(newJob) ;
    			JSONObject props = newJob.getJSONObject("props");
    			// create folder
    			String folderName= props.get("folderName").toString();
    			createFolder(folderName);
    			// verify folder was created
    			Folder targetFolder= getFolder(folderName);
    			if (targetFolder == null) {
    				log.debug(logPrefix + "createJob - target folder not retrieved. Exiting creation process");
    			} else {
    				log.debug(logPrefix + "createJob - target folder retrieved !!!!!");
        			// create job in target folder
        			String jobSrc= props.get("source").toString();
        			String jobName= props.get("jobName").toString();
        			Collection<String> existingJobs= getJobNames();
        			if (existingJobs.contains(jobName)) {
        				// do not create
        				log.debug(logPrefix + "Job " + jobName + " already exists.");
        			} else {
        				createJobInFolder(targetFolder, jobName, jobSrc);
        			}
    			}
    		} catch (Exception e) {
    			log.error(logPrefix + "An unexpected error occurred while creating job.");
                e.printStackTrace();
            } finally {
            	// be sure to re-enable security
            	Jenkins.getInstance().setSecurityRealm(securityRealm);
            	Jenkins.getInstance().setAuthorizationStrategy(authorizationStrategy);
            }
        }
    }

    private static void createCredentials(JSONObject newJob) {
    	if(newJob.has("props")) {
            JSONObject props = newJob.getJSONObject("props");
            if (props.has("userName") && props.has("password")) { // not all jobs require creds creation
        		try {
                	log.debug(logPrefix + "createCredentials - creating " + BLX_CREDS + " credentials.");
        			Credentials creds = (Credentials) new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, BLX_CREDS, BLX_CREDS_DESC, props.get("userName").toString(), props.get("password").toString());
        			SystemCredentialsProvider.getInstance().getCredentials().add(creds);
        	        SystemCredentialsProvider.getInstance().save();
        			log.debug(logPrefix + "createCredentials " + BLX_CREDS + " successfully created.");
        		} catch (Exception e) {
        			log.error(logPrefix + "createCredentials - unable to  create " + BLX_CREDS + " credentials.");
        			e.printStackTrace();
        		}
            } else {
            	log.debug(logPrefix + "createCredentials - credentials creation not required.");
            }
        }
    }

    private static boolean validCreationRequest(JSONObject newJob) {
    	log.debug(logPrefix + "validCreationRequest - Validating creation payload.");
    	boolean valid= false;
    	if(newJob.has("props")) {
            JSONObject props = newJob.getJSONObject("props");
            if (props.has("folderName") && props.has("jobName") && props.has("source")) {
            	log.debug(logPrefix + "validCreationRequest - Payload is valid.");
            	valid= true;
            } else {
            	log.error(logPrefix + "validCreationRequest - Payload is not valid!");
            }
        }
    	return valid;
    }

    private static void createFolder(String folderName) {
    	log.debug(logPrefix + "createFolder - Creating folder " + folderName);
    	try {
    		Jenkins.getInstance().createProjectFromXML(folderName, new ByteArrayInputStream(FOLDER_SPEC.getBytes()));
    		log.debug(logPrefix + folderName + " was created successfully!");
    	} catch (Exception e) {
    		// folder might be existing
    		log.debug(logPrefix + folderName + " was not created.");
            // e.printStackTrace();
        }
    }

    private static void createJobInFolder(Folder targetFolder, String jobName, String source) {
    	log.debug(logPrefix + "createItem - Creating job " + jobName);
    	try {
    		targetFolder.createProjectFromXML(jobName, new ByteArrayInputStream(source.getBytes()));
    		log.debug(logPrefix + jobName + " was created successfully!");
    	} catch (Exception e) {
    		log.error(logPrefix + jobName + " was not created.");
            e.printStackTrace();
        }
    }

    private static void disableSecurity() {
    	log.debug(logPrefix + "disableSecurity()");
    	Jenkins.getInstance().disableSecurity();
    }

    private static Folder getFolder(String folderName) {
    	return (Folder) Jenkins.getInstance().getItem(folderName);
    }
}