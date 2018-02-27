/*
 <notice>

 Copyright 2016, 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.connect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.devops.connect.DevOpsGlobalConfiguration;

import net.sf.json.JSONObject;
import net.sf.json.JSONArray;

import com.google.gson.*;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.kohsuke.stapler.*;
import javax.xml.bind.DatatypeConverter;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.TimeZone;
import java.net.URLEncoder;

import org.apache.commons.codec.binary.Base64;

import com.ibm.devops.connect.Endpoints.EndpointManager;

import org.jenkinsci.plugins.uniqueid.IdStore;

import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;

public class CloudPublisher  {
	public static final Logger log = LoggerFactory.getLogger(CloudPublisher.class);
	private String logPrefix= "[IBM Cloud DevOps] CloudPublisher#";

    private final String JENKINS_JOB_ENDPOINT_URL = "api/v1/jenkins/jobs";
    private final String JENKINS_JOB_STATUS_ENDPOINT_URL = "api/v1/jenkins/jobStatus";
    private final String INTEGRATIONS_ENDPOINT_URL = "api/v1/integrations";
    private final String INTEGRATION_ENDPOINT_URL = "api/v1/integrations/{integration_id}";

    private static String BUILD_API_URL = "/organizations/{org_name}/toolchainids/{toolchain_id}/buildartifacts/{build_artifact}/builds";
    private final static String CONTENT_TYPE_JSON = "application/json";
    private final static String CONTENT_TYPE_XML = "application/xml";

    // form fields from UI
    private String applicationName;
    private String orgName;
    private String credentialsId;
    private String toolchainName;

    private String dlmsUrl;
    private PrintStream printStream;
    private File root;
    private static String bluemixToken;
    private static String preCredentials;

    // fields to support jenkins pipeline
    private String result;
    private String gitRepo;
    private String gitBranch;
    private String gitCommit;
    private String username;
    private String password;
    // optional customized build number
    private String buildNumber;

    public CloudPublisher() {
    }

    private String getSyncApiUrl() {
        EndpointManager em = new EndpointManager();
        return em.getSyncApiEndpoint();
    }

    private String getSyncStoreUrl() {
        EndpointManager em = new EndpointManager();
        return em.getSyncStoreEndpoint();
    }

    /**
     * Upload the build information to Sync API - API V1.
     */
    public boolean uploadJobInfo(JSONObject jobJson) {
        String url = this.getSyncApiUrl() + JENKINS_JOB_ENDPOINT_URL;

        JSONArray payload = new JSONArray();
        payload.add(jobJson);

        return postToSyncAPI(url, payload.toString());
    }

    public boolean uploadJobStatus(JSONObject jobStatus) {

        String url = this.getSyncApiUrl() + JENKINS_JOB_STATUS_ENDPOINT_URL;
        return postToSyncAPI(url, jobStatus.toString());
    }

    private boolean postToSyncAPI(String url, String payload) {
    	String localLogPrefix= logPrefix + "uploadJobInfo ";

        String resStr = "";

        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();

            JenkinsIntegrationId jenkinsIntegrationId = new JenkinsIntegrationId();
            String jenkinsId = jenkinsIntegrationId.getIntegrationId();

            HttpPost postMethod = new HttpPost(url);
            // postMethod = addProxyInformation(postMethod);
            postMethod.setHeader("sync_token", Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncToken());
            postMethod.setHeader("sync_id", Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncId());
            postMethod.setHeader("instance_type", "JENKINS");
            postMethod.setHeader("instance_id", jenkinsId);
            postMethod.setHeader("integration_id", jenkinsId);
            postMethod.setHeader("Content-Type", "application/json");

            StringEntity data = new StringEntity(payload);
            postMethod.setEntity(data);

            CloseableHttpResponse response = httpClient.execute(postMethod);

            resStr = EntityUtils.toString(response.getEntity());
            if (response.getStatusLine().toString().contains("200")) {
                // get 200 response
                log.info(localLogPrefix + "Upload Job Information successfully");
                return true;

            } else {
                // if gets error status
                log.error(localLogPrefix + "Error: Failed to upload Job, response status " + response.getStatusLine());
            }
        } catch (JsonSyntaxException e) {
            log.error(localLogPrefix + "Invalid Json response, response: " + resStr);
        } catch (IllegalStateException e) {
            // will be triggered when 403 Forbidden
            try {
                log.error(localLogPrefix + "Please check if you have the access to " + URLEncoder.encode(this.orgName, "UTF-8") + " org");
            } catch (UnsupportedEncodingException e1) {
                e1.printStackTrace();
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean doesIntegrationExist() {
    	String localLogPrefix = logPrefix + "doesIntegrationExist ";
        String resStr = "";
        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();

            JenkinsIntegrationId jenkinsIntegrationId = new JenkinsIntegrationId();
            String jenkinsId = jenkinsIntegrationId.getIntegrationId();

            String url = this.getSyncStoreUrl() + INTEGRATION_ENDPOINT_URL.replace("{integration_id}", jenkinsId);

            HttpGet getMethod = new HttpGet(url);
            // postMethod = addProxyInformation(postMethod);
            getMethod.setHeader("Content-Type", "application/json");

            String authEncoding = DatatypeConverter.printBase64Binary((Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncId() + ":" + Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncToken()).getBytes("UTF-8"));
            getMethod.setHeader("Authorization", "Basic " + authEncoding);

            CloseableHttpResponse response = httpClient.execute(getMethod);

            resStr = EntityUtils.toString(response.getEntity());
            if (response.getStatusLine().toString().contains("200") || response.getStatusLine().toString().contains("201")) {
                // get 200 response
                log.info(localLogPrefix + "Integration was retrieved");

                JSONObject jsonBody = JSONObject.fromObject(resStr);

                String serverUrl = Jenkins.getInstance().getRootUrl();

                if(!serverUrl.equals(jsonBody.get("serverUrl"))) {
                    log.info(localLogPrefix + "Must update server url on integration...");
                    updateIntegrationServerUrl(jsonBody, serverUrl);
                }

                return true;

            } else {

                log.info(localLogPrefix + "No Integration Retrieved");

                return false;
            }
        } catch (JsonSyntaxException e) {
            log.error(localLogPrefix + "Invalid Json response, response: " + resStr);
        } catch (IllegalStateException e) {
            // will be triggered when 403 Forbidden
            try {
                log.info(localLogPrefix + "Please check if you have the access to " + URLEncoder.encode(this.orgName, "UTF-8") + " org");
            } catch (UnsupportedEncodingException e1) {
                e1.printStackTrace();
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean createIntegration() {
    	String localLogPrefix= logPrefix + "createIntegration ";
        String resStr = "";

        JenkinsIntegrationId jenkinsIntegrationId = new JenkinsIntegrationId();
        String jenkinsId = jenkinsIntegrationId.getIntegrationId();

        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();

            String url = this.getSyncStoreUrl() + INTEGRATIONS_ENDPOINT_URL;

            JSONObject newIntegration = new JSONObject();

            newIntegration.put("name", "Jenkins Plugin Integration - " + jenkinsId);
            newIntegration.put("syncId", Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncId());
            newIntegration.put("id", jenkinsId);
            newIntegration.put("dateCreated", System.currentTimeMillis());
            newIntegration.put("docType", "integration");
            newIntegration.put("serverUrl", Jenkins.getInstance().getRootUrl());
            newIntegration.put("type", "JENKINS");

            HttpPost postMethod = new HttpPost(url);
            // postMethod = addProxyInformation(postMethod);
            postMethod.setHeader("Content-Type", "application/json");
            String authEncoding = DatatypeConverter.printBase64Binary((Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncId() + ":" + Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncToken()).getBytes("UTF-8"));
            postMethod.setHeader("Authorization", "Basic " + authEncoding);

            postMethod.setHeader("syncId", Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncId());

            StringEntity data = new StringEntity(newIntegration.toString());
            postMethod.setEntity(data);

            CloseableHttpResponse response = httpClient.execute(postMethod);

            resStr = EntityUtils.toString(response.getEntity());
            if (response.getStatusLine().toString().contains("200") || response.getStatusLine().toString().contains("201")) {
                // get 200 response
                log.info("===================================================");
                log.info(localLogPrefix + "Created integration successfully");
                return true;

            } else {
                // if gets error status
                log.error(localLogPrefix + "Error: Failed to create integration, response status " + response.getStatusLine());
            }
        } catch (JsonSyntaxException e) {
            log.error(localLogPrefix + "Invalid Json response, response: " + resStr);
        } catch (IllegalStateException e) {
            // will be triggered when 403 Forbidden
            try {
                log.error(localLogPrefix + "Please check if you have the access to " + URLEncoder.encode(this.orgName, "UTF-8") + " org");
            } catch (UnsupportedEncodingException e1) {
                e1.printStackTrace();
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean updateIntegrationServerUrl(JSONObject payload, String newServerUrl) {
    	String localLogPrefix= logPrefix + "updateIntegrationServerUrl ";

        payload.put("serverUrl", newServerUrl);

        return (updateIntegration(payload));
    }

    private boolean updateIntegration(JSONObject payload) {
    	String localLogPrefix= logPrefix + "updateIntegrationServerUrl ";

        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();

            String url = this.getSyncStoreUrl() + INTEGRATIONS_ENDPOINT_URL;

            HttpPost putMethod = new HttpPost(url);
            // putMethod = addProxyInformation(putMethod);
            putMethod.setHeader("Content-Type", "application/json");
            String authEncoding = DatatypeConverter.printBase64Binary((Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncId() + ":" + Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncToken()).getBytes("UTF-8"));
            putMethod.setHeader("Authorization", "Basic " + authEncoding);

            putMethod.setHeader("syncId", Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncId());

            StringEntity data = new StringEntity(payload.toString());
            putMethod.setEntity(data);

            CloseableHttpResponse response = httpClient.execute(putMethod);

            if (response.getStatusLine().toString().contains("200") || response.getStatusLine().toString().contains("201")) {
                log.info(localLogPrefix + "Upated integration successfully");
                return true;

            } else {
                // if gets error status
                log.error(localLogPrefix + "Error: Failed to update integration, response status " + response.getStatusLine());
            }
        } catch (JsonSyntaxException e) {
            log.error(localLogPrefix + "Invalid Json response, response");
        } catch (IllegalStateException e) {
            // will be triggered when 403 Forbidden
            try {
                log.error(localLogPrefix + "Please check if you have the access to " + URLEncoder.encode(this.orgName, "UTF-8") + " org");
            } catch (UnsupportedEncodingException e1) {
                e1.printStackTrace();
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean doesOtherIntegrationExist() {
        String localLogPrefix= logPrefix + "doesOtherIntegrationExist ";

        String resStr = "";

        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();

            JenkinsIntegrationId jenkinsIntegrationIdGenerator = new JenkinsIntegrationId();
            String jenkinsIntegrationId = jenkinsIntegrationIdGenerator.getIntegrationId();

            String url = this.getSyncStoreUrl() + INTEGRATIONS_ENDPOINT_URL;

            HttpGet getMethod = new HttpGet(url);
            // postMethod = addProxyInformation(postMethod);
            getMethod.setHeader("Content-Type", "application/json");

            String authEncoding = DatatypeConverter.printBase64Binary((Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncId() + ":" + Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getSyncToken()).getBytes("UTF-8"));
            getMethod.setHeader("Authorization", "Basic " + authEncoding);

            CloseableHttpResponse response = httpClient.execute(getMethod);

            resStr = EntityUtils.toString(response.getEntity());
            if (response.getStatusLine().toString().contains("200") || response.getStatusLine().toString().contains("201")) {

                JSONArray jsonBody = JSONArray.fromObject(resStr);

                boolean updatedOldIntegration = false;
                boolean integrationFound = false;

                for (int i = 0; i < jsonBody.size(); i++) {
                    JSONObject integrationObj = jsonBody.getJSONObject(i);
                    updatedOldIntegration = updateLegacyIntegrationIfNecessary(integrationObj);
                    if(integrationObj.get("id").equals(jenkinsIntegrationId)) {
                        integrationFound = true;
                    }
                }

                if(!updatedOldIntegration && jsonBody.size() > 0 && !integrationFound) {
                    return true;
                }
            }
        } catch (JsonSyntaxException e) {
            log.error(localLogPrefix + "Invalid Json response, response: " + resStr);
        } catch (IllegalStateException e) {
            // will be triggered when 403 Forbidden
            try {
                log.info(localLogPrefix + "Please check if you have the access to " + URLEncoder.encode(this.orgName, "UTF-8") + " org");
            } catch (UnsupportedEncodingException e1) {
                e1.printStackTrace();
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    /*
    *   currently, the integration ID is the sync ID concatenated with an underscore and the Jenkins Server ID.  The original integration ID was
    *   just the Jenkins Server ID.  If we find the Jenkins Server Id, we update that record with the proper Integration Id
    */

    private boolean updateLegacyIntegrationIfNecessary(JSONObject integrationObj) {
        String jenkinsId;
        if (IdStore.getId(Jenkins.getInstance()) != null) {
            jenkinsId = IdStore.getId(Jenkins.getInstance());
        } else {
            IdStore.makeId(Jenkins.getInstance());
            jenkinsId = IdStore.getId(Jenkins.getInstance());
        }

        if(integrationObj.get("id").equals(jenkinsId)) {
            JenkinsIntegrationId jenkinsIntegrationId = new JenkinsIntegrationId();
            integrationObj.put("id", jenkinsIntegrationId.getIntegrationId());
            return updateIntegration(integrationObj);
        }

        return false;
    }
}
