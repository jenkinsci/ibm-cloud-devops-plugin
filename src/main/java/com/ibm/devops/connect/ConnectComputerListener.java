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

@Extension
public class ConnectComputerListener extends ComputerListener {

    @Override
    public void onOnline(Computer c) {

        String url = "https://uccloud-connect-stage1.stage1.mybluemix.net";

        CloudWorkListener listener = new CloudWorkListener();
        CloudSocketComponent socket = new CloudSocketComponent(listener, url);

        try {
            socket.connectToCloudServices();
        } catch (Exception e) {
            System.out.println("WE CAUGHT AN EXCEPTION: " + e);
        }
    }

}