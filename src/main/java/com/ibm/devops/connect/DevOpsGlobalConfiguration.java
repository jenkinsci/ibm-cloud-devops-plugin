/*
 <notice>

 Copyright 2016, 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.connect;

import java.util.List;

import hudson.CopyOnWrite;
import hudson.Extension;
import hudson.model.Computer;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.AncestorInPath;
import hudson.util.FormFieldValidator;
import com.ibm.devops.connect.CloudSocketComponent;
import com.ibm.devops.connect.ConnectComputerListener;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.security.ACL;
import jenkins.model.Jenkins;

import java.io.IOException;
import javax.servlet.ServletException;
/**
 * Created by lix on 7/20/17.
 */
@Extension(ordinal = 100)
public class DevOpsGlobalConfiguration extends GlobalConfiguration {

    @CopyOnWrite
    private volatile String syncId;
    private volatile String syncToken;
    private String credentialsId;

    public DevOpsGlobalConfiguration() {
        load();
    }

    public String getSyncId() {
    	return syncId;
    }

    public void setSyncId(String syncId) {
        this.syncId = syncId;
        save();
    }

    public String getSyncToken() {
    	return syncToken;
    }

    public void setSyncToken(String syncToken) {
        this.syncToken = syncToken;
        save();
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public void setCredentialsId(String crendentialsId) {
        this.credentialsId = credentialsId;
        save();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
        // To persist global configuration information,
        // set that to properties and call save().
        syncId = formData.getString("syncId");
        syncToken = formData.getString("syncToken");
       credentialsId = formData.getString("credentialsId");
        save();

        reconnectCloudSocket();

        return super.configure(req,formData);
    }

    // for the future multi-region use
    public ListBoxModel doFillRegionItems() {
        ListBoxModel items = new ListBoxModel();
        return items;
    }

    @Deprecated
    public void doTestConnection(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        new FormFieldValidator(req, rsp, true) {
            @Override
            protected void check() throws IOException, ServletException {
                CloudSocketComponent socket = new ConnectComputerListener().getCloudSocketInstance();
                if(socket.connected()) {
                    ok("Success - Connected to IBM Cloud Service");
                } else {
                    String cause = socket.getCauseOfFailure();
                    if(cause != null) {
                        error("Not connected to IBM Cloud Services - " + cause);
                    } else {
                        error("Not connected to IBM Cloud Services - Please ensure that the current values are applied");
                    }
                }
            }
        }.process();
    }

    /**
    * This method is called to populate the credentials list on the Jenkins config page.
    */
    public ListBoxModel doFillCredentialsIdItems(@QueryParameter("target") final String target) {
        StandardListBoxModel result = new StandardListBoxModel();
        result.includeEmptyValue();
        result.withMatching(CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
                CredentialsProvider.lookupCredentials(
                        StandardUsernameCredentials.class,
                        Jenkins.getInstance(),
                        ACL.SYSTEM,
                        URIRequirementBuilder.fromUri(target).build()
                )
        );
        return result;
    }

    public StandardUsernamePasswordCredentials getCredentialsObj() {
        List<StandardUsernamePasswordCredentials> standardCredentials = CredentialsProvider.lookupCredentials(
                    StandardUsernamePasswordCredentials.class,
                    Jenkins.getInstance(),
                    ACL.SYSTEM);

        StandardUsernamePasswordCredentials credentials =
                CredentialsMatchers.firstOrNull(standardCredentials, CredentialsMatchers.withId(this.credentialsId));

        return credentials;
    }

    private void reconnectCloudSocket() {
        ConnectComputerListener connectComputerListener = new ConnectComputerListener();

        connectComputerListener.onOnline(Computer.currentComputer());
    }
}
