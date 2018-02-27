/*******************************************************************************
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2017. All Rights Reserved.
 *
 * Note to U.S. Government Users Restricted Rights:  Use,
 * duplication or disclosure restricted by GSA ADP Schedule
 * Contract with IBM Corp.
 *******************************************************************************/
package com.ibm.devops.connect;

import java.net.URI;
import java.util.Properties;

import jenkins.model.Jenkins;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.devops.connect.DevOpsGlobalConfiguration;

import com.ibm.cloud.urbancode.connect.client.ConnectSocket;
import com.ibm.cloud.urbancode.connect.client.Listeners;
import com.ibm.devops.connect.OnConnectListener;

import com.ibm.devops.connect.CloudPublisher;

import io.socket.client.Socket;

public class CloudSocketComponent {

    public static final Logger log = LoggerFactory.getLogger(CloudSocketComponent.class);
    private String logPrefix= "[IBM Cloud DevOps] CloudSocketComponent#";

    final private IWorkListener workListener;
    final private String cloudUrl;
    private ConnectSocket socket;

    private static boolean otherIntegrationExists = false;

    public CloudSocketComponent(IWorkListener workListener, String cloudUrl) {
        this.workListener = workListener;
        this.cloudUrl = cloudUrl;
    }

    public boolean isRegistered() {
        return StringUtils.isNotBlank(getSyncToken());
    }

    public String getSyncId() {
    	return Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncId();
    }

    public String getSyncToken() {
    	return Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncToken();
    }

    public void connectToCloudServices() throws Exception {
    	logPrefix= logPrefix + "connectToCloudServices ";
        String syncId = getSyncId();
        String syncToken = getSyncToken();
        if (StringUtils.isBlank(syncId) || StringUtils.isBlank(syncToken)) {
            log.info(logPrefix + "Not connecting to the cloud. IBM Bluemix DevOps Connect not registered yet.");
            return;
        }

        CloudPublisher cloudPublisher = new CloudPublisher();

        boolean shouldConnect = true;
        // Does integration exist
        if(!cloudPublisher.doesIntegrationExist()) {
            // Does another integration exist
            if(cloudPublisher.doesOtherIntegrationExist()) {
                log.warn(logPrefix + "These credentials have been used by another Jenkins Instance.  Please generate another Sync Id and provide those credentials here.");
                shouldConnect = false;
                otherIntegrationExists = true;
            } else {
                // Create Integration
                cloudPublisher.createIntegration();
            }
        } else {
            otherIntegrationExists = false;
        }

        if(shouldConnect) {
            URI uri = new URI(cloudUrl);
            log.info(logPrefix + "Starting cloud endpoint " + syncId);
            socket = ConnectSocket.builder()
                .uri(uri)
                .id(syncId)
                .token(syncToken)
                .onConnect(Listeners.chain(Listeners.chain(Listeners.INFO_LOGGING, Listeners.EMIT_GET_WORK), OnConnectListener.BUILD_JOBS_LIST))
                .onDisconnect(Listeners.INFO_LOGGING)
                .onWorkAvailable(Listeners.chain(Listeners.DEBUG_LOGGING, Listeners.EMIT_GET_WORK))
                .onWork(workListener)
    //            .onWork(Listeners.chain(Listeners.INFO_LOGGING, workListener))
                .onError(Listeners.ERROR_LOGGING)
                .build();
            socket.on(Socket.EVENT_CONNECT_ERROR, Listeners.ERROR_LOGGING);
            socket.on(Socket.EVENT_CONNECT_TIMEOUT, Listeners.ERROR_LOGGING);
            socket.on(Socket.EVENT_RECONNECT_ERROR, Listeners.ERROR_LOGGING);
            socket.on(Socket.EVENT_RECONNECT_FAILED, Listeners.ERROR_LOGGING);
            socket.on(Socket.EVENT_RECONNECT_ATTEMPT, Listeners.INFO_LOGGING);
            // do not listen for Socket.EVENT_RECONNECT, we will make 2 get work requests

            socket.connect();
        }
    }

    // this does get called, but you may not see logging in the console. it will appear in the file.
    public void disconnect() {
        if (socket != null) {
            try {
                socket.disconnect();
                log.info(logPrefix + "Disconnected from the cloud service");
            }
            catch (Exception e) {
                log.error(logPrefix + "Error disconnecting the cloud service gracefully", e);
            }
            finally {
                socket = null;
            }
        }
    }

    public boolean connected() {
        if(socket == null) {
            return false;
        }
        return socket.connected();
    }

    public String getCauseOfFailure() {
        if (otherIntegrationExists) {
            return "These credentials have been used by another Jenkins Instance.  Please generate another Sync Id and provide those credentials here.";
        }

        return null;
    }
}
