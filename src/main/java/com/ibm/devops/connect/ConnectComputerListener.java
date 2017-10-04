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
    @Override
    public void onOnline(Computer c) {

        String url = getConnectUrl();

        CloudWorkListener listener = new CloudWorkListener();
        CloudSocketComponent socket = new CloudSocketComponent(listener, url);

        try {
        	log.info("Connecting to Cloud Services...");
            socket.connectToCloudServices();
            log.info("Connected to Cloud Services!");
        } catch (Exception e) {
            log.error("Exception caught while connecting to Cloud Services: " + e);
        }
    }

    private String getConnectUrl() {
        return "https://uccloud-connect-stage1.stage1.mybluemix.net";
    }
}