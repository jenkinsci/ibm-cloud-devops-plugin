/*
 <notice>

 Copyright 2016, 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.dra;

import hudson.CopyOnWrite;
import hudson.Extension;
import hudson.model.Computer;
import hudson.util.ListBoxModel;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerResponse;
import hudson.util.FormFieldValidator;
import com.ibm.devops.connect.CloudSocketComponent;
import com.ibm.devops.connect.ConnectComputerListener;

import java.io.IOException;
import javax.servlet.ServletException;
/**
 * Created by lix on 7/20/17.
 */
@Extension(ordinal = 100)
public class DevOpsGlobalConfiguration extends GlobalConfiguration {

    @CopyOnWrite
    private volatile String consoleUrl;
    private volatile boolean debug_mode;
    private volatile String syncId;
    private volatile String syncToken;

    public DevOpsGlobalConfiguration() {
        load();
    }

    public String getConsoleUrl() {
        return consoleUrl;
    }

    public boolean isDebug_mode() {
        return debug_mode;
    }

    public void setDebug_mode(boolean debug_mode) {
        this.debug_mode = debug_mode;
        save();
    }

    public void setConsoleUrl(String consoleUrl) {
        this.consoleUrl = consoleUrl;
        save();
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

    @Override
    public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
        // To persist global configuration information,
        // set that to properties and call save().
        consoleUrl = formData.getString("consoleUrl");
        debug_mode = Boolean.parseBoolean(formData.getString("debug_mode"));
        syncId = formData.getString("syncId");
        syncToken = formData.getString("syncToken");
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
                    error("Not connected to IBM Cloud Services - Please ensure that current values are applied");
                }
            }
        }.process();
    }

    private void reconnectCloudSocket() {
        ConnectComputerListener connectComputerListener = new ConnectComputerListener();

        connectComputerListener.onOnline(Computer.currentComputer());
    }
}
