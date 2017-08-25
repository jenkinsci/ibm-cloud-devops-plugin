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
import hudson.model.TopLevelItem;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import jenkins.model.Jenkins;

import org.apache.commons.lang.builder.ToStringBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jenkins server
 */

public class JenkinsServer {
	public static final Logger log = LoggerFactory.getLogger(JenkinsServer.class);
	
	// not used yet - but might be used later
    public static Collection<String> getJobNames() {
    	log.info("JenkinsServer: getJobNames()");
    	Collection<String> allJobNames= Jenkins.getInstance().getJobNames();
    	log.info("retrieved " + allJobNames.size() + " JobNames");
    	for (Iterator iterator = allJobNames.iterator(); iterator.hasNext();) {
    		String aJobName = (String) iterator.next(); 
    		log.info("job: " + aJobName);
    	}
    	return Jenkins.getInstance().getJobNames();
    }
    
    public static List<Item> getAllItems() {
    	log.info("JenkinsServer: getAllItems()");
    	List<AbstractItem> allProjects= Jenkins.getInstance().getAllItems(AbstractItem.class);
    	log.info("Retrieved " + allProjects.size() + " projects");
    	return Jenkins.getInstance().getAllItems();
    }
}
