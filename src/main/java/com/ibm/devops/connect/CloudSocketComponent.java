/*******************************************************************************
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2014. All Rights Reserved.
 *
 * Note to U.S. Government Users Restricted Rights:  Use,
 * duplication or disclosure restricted by GSA ADP Schedule
 * Contract with IBM Corp.
 *******************************************************************************/
package com.urbancode.jenkins.plugins.ucrelease;

import java.net.URI;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.cloud.urbancode.connect.client.ConnectSocket;
import com.ibm.cloud.urbancode.connect.client.Listeners;

import io.socket.client.Socket;

public class CloudSocketComponent {

    public static final Logger log = LoggerFactory.getLogger(CloudSocketComponent.class);

    final private IWorkListener workListener;
    final private String cloudUrl;
    private ConnectSocket socket;
    
    public CloudSocketComponent(IWorkListener workListener, String cloudUrl) {
        this.workListener = workListener;
        this.cloudUrl = cloudUrl;
    }
    
    public boolean isRegistered() {
        return StringUtils.isNotBlank(getSyncToken());
    }

    public String getSyncId() {
        return "ce64dea4-6728-46da-ad62-b371706755c9";
        // Properties props = SyncApplicationProperties.getProperties();
        // return props.getProperty(SyncApplicationProperties.SYNC_ID);
    }

    public String getSyncToken() {
        return "KPZLS8cRrR1Llz2aa7slwV7w9p6Kou8bhcZSvSQ0Xxb6WW1GmwbQF5oRuRBcyXUJsBF2b9SxQ5fA40avD7NqwA";
        // Properties props = SyncApplicationProperties.getProperties();
        // return props.getProperty(SyncApplicationProperties.SYNC_TOKEN);
    }

    public void connectToCloudServices() throws Exception {
        String syncId = getSyncId();
        String syncToken = getSyncToken();
        if (StringUtils.isBlank(syncId) || StringUtils.isBlank(syncToken)) {
            log.info("Not connecting to the cloud. IBM Bluemix DevOps Connect not registered yet.");
            return;
        }
        URI uri = new URI(cloudUrl);
        log.info("Starting cloud endpoint " + syncId);
        socket = ConnectSocket.builder()
            .uri(uri)
            .id(syncId)
            .token(syncToken)
            .onConnect(Listeners.chain(Listeners.INFO_LOGGING, Listeners.EMIT_GET_WORK))
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
        log.info("Connecting to cloud service at {}", uri);
        socket.connect();

        System.out.println("---------------->>>>>>>>>>>>>>>>>>>");
        System.out.println("WE HAVE CONNECTED TO THE SERVER.....");

    }
    
    // this does get called, but you may not see logging in the console. it will appear in the file.
    public void disconnect() {
        if (socket != null) {
            try {
                socket.disconnect();
                log.info("Disconnected from the cloud service");
            }
            catch (Exception e) {
                log.error("Error disconnecting the cloud service gracefully", e);
            }
            finally {
                socket = null;
            }
        }
    }
}
