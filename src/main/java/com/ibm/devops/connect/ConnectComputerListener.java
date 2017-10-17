/*******************************************************************************
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2017. All Rights Reserved.
 *
 * Note to U.S. Government Users Restricted Rights:  Use,
 * duplication or disclosure restricted by GSA ADP Schedule
 * Contract with IBM Corp.
 *******************************************************************************/
package com.ibm.devops.connect;

import hudson.slaves.ComputerListener;
import hudson.model.Computer;
import hudson.Extension;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Extension
public class ConnectComputerListener extends ComputerListener {
	public static final Logger log = LoggerFactory.getLogger(ConnectComputerListener.class);
    private String logPrefix= "[IBM Cloud DevOps] ConnectComputerListener#";
    
    private static CloudSocketComponent cloudSocketInstance;

    @Override
    public void onOnline(Computer c) {
        String url = getConnectUrl();

    	logPrefix= logPrefix + "onOnline ";

        CloudWorkListener listener = new CloudWorkListener();

        if(cloudSocketInstance != null && cloudSocketInstance.connected()) {
            cloudSocketInstance.disconnect();
        }

        cloudSocketInstance = new CloudSocketComponent(listener, url);

        try {
        	log.info(logPrefix + "Connecting to Cloud Services...");
            getCloudSocketInstance().connectToCloudServices();
            if(getCloudSocketInstance().connected()) {
                log.info(logPrefix + "Connected to Cloud Services!");
            } else {
                log.warn(logPrefix + "Failed to connected to Cloud Services");
            }
        } catch (Exception e) {
            log.error(logPrefix + "Exception caught while connecting to Cloud Services: " + e);
        }
    }

    private String getConnectUrl() {
        return "https://uccloud-connect-stage1.stage1.mybluemix.net";
    }

    public CloudSocketComponent getCloudSocketInstance() {
        return ConnectComputerListener.cloudSocketInstance;
    }
}