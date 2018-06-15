/*
 <notice>

 Copyright 2016, 2017 IBM Corporation

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 </notice>
 */

package com.ibm.devops.dra;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.gson.*;
import hudson.*;
import hudson.model.*;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

import static com.ibm.devops.dra.UIMessages.*;

/**
 * Authenticate with Bluemix and then upload the result file to DRA
 */
public class PublishTest extends AbstractDevOpsAction implements SimpleBuildStep {

    private final static String API_PART = "/toolchainids/{toolchain_id}/buildartifacts/{build_artifact}/builds/{build_id}/results_multipart";
    private final static String CONTENT_TYPE_JSON = "application/json";
    private final static String CONTENT_TYPE_XML = "application/xml";
    private final static String CONTENT_TYPE_LCOV = "text/plain";

    // form fields from UI
    private final String lifecycleStage;
    private String contents;
    private String additionalLifecycleStage;
    private String additionalContents;
    private String buildNumber;
    private String applicationName;
    private String buildJobName;
    private String toolchainName;
    private String credentialsId;
    private String policyName;
    private boolean willDisrupt;

    private EnvironmentScope testEnv;
    private String envName;
    private boolean isDeploy;

    private PrintStream printStream;
    private File root;
    private static String bluemixToken;
    private static String preCredentials;

    //fields to support jenkins pipeline
    private String username;
    private String password;
    private String apikey;

    @DataBoundConstructor
    public PublishTest(String lifecycleStage,
                       String contents,
                       String applicationName,
                       String toolchainName,
                       String buildJobName,
                       String credentialsId,
                       OptionalUploadBlock additionalUpload,
                       OptionalBuildInfo additionalBuildInfo,
                       OptionalGate additionalGate,
                       EnvironmentScope testEnv) {
        this.lifecycleStage = lifecycleStage;
        this.contents = contents;
        this.credentialsId = credentialsId;
        this.applicationName = applicationName;
        this.toolchainName = toolchainName;
        this.buildJobName = buildJobName;
        this.testEnv = testEnv;
        this.envName = testEnv.getEnvName();
        this.isDeploy = testEnv.isDeploy();

        if (additionalUpload == null) {
            this.additionalContents = null;
            this.additionalLifecycleStage = null;
        } else {
            this.additionalLifecycleStage = additionalUpload.additionalLifecycleStage;
            this.additionalContents = additionalUpload.additionalContents;
        }

        if (additionalBuildInfo == null) {
            this.buildNumber = null;
        } else {
            this.buildNumber = additionalBuildInfo.buildNumber;
        }

        if (additionalGate == null) {
            this.policyName = null;
            this.willDisrupt = false;
        } else {
            this.policyName = additionalGate.getPolicyName();
            this.willDisrupt = additionalGate.isWillDisrupt();
        }
    }

    public PublishTest(HashMap<String, String> envVarsMap, HashMap<String, String> paramsMap) {
        this.lifecycleStage = paramsMap.get("type");
        this.contents = paramsMap.get("fileLocation");

        this.applicationName = envVarsMap.get(APP_NAME);
        this.toolchainName = envVarsMap.get(TOOLCHAIN_ID);

        if (Util.isNullOrEmpty(envVarsMap.get(API_KEY))) {
            this.username = envVarsMap.get(USERNAME);
            this.password = envVarsMap.get(PASSWORD);
        } else {
            this.apikey = envVarsMap.get(API_KEY);
        }
    }

    public void setBuildNumber(String buildNumber) {
        this.buildNumber = buildNumber;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getApplicationName() {
        return applicationName;
    }

    public String getToolchainName() {
        return toolchainName;
    }

    public String getBuildJobName() {
        return buildJobName;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getLifecycleStage() {
        return lifecycleStage;
    }

    public String getContents() {
        return contents;
    }

    public String getAdditionalLifecycleStage() {
        return additionalLifecycleStage;
    }

    public String getAdditionalContents() {
        return additionalContents;
    }

    public String getBuildNumber() {
        return buildNumber;
    }

    public String getPolicyName() {
        return policyName;
    }

    public boolean isWillDisrupt() {
        return willDisrupt;
    }

    public EnvironmentScope getTestEnv() {
        return testEnv;
    }

    public String getEnvName() {
        return envName;
    }

    public void setEnvName(String envName) {
        this.envName = envName;
    }

    public boolean isDeploy() {
        return isDeploy;
    }

    /**
     * Sub class for Optional Upload Block
     */
    public static class OptionalUploadBlock {
        private String additionalLifecycleStage;
        private String additionalContents;

        @DataBoundConstructor
        public OptionalUploadBlock(String additionalLifecycleStage, String additionalContents) {
            this.additionalLifecycleStage = additionalLifecycleStage;
            this.additionalContents = additionalContents;
        }
    }

    public static class OptionalBuildInfo {
        private String buildNumber;

        @DataBoundConstructor
        public OptionalBuildInfo(String buildNumber) {
            this.buildNumber = buildNumber;
        }
    }

    public static class OptionalGate {
        private String policyName;
        private boolean willDisrupt;

        @DataBoundConstructor
        public OptionalGate(String policyName, boolean willDisrupt) {
            this.policyName = policyName;
            setWillDisrupt(willDisrupt);
        }

        public String getPolicyName() {
            return policyName;
        }

        public boolean isWillDisrupt() {
            return willDisrupt;
        }

        @DataBoundSetter
        public void setWillDisrupt(boolean willDisrupt) {
            this.willDisrupt = willDisrupt;
        }
    }


    @Override
    public void perform(@Nonnull Run build, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {

        printStream = listener.getLogger();
        printPluginVersion(this.getClass().getClassLoader(), printStream);

        // create root dir for storing test result
        root = new File(build.getRootDir(), "DRA_TestResults");

        // Get the project name and build id from environment and expand the vars
        EnvVars envVars = build.getEnvironment(listener);
        this.applicationName = envVars.expand(this.applicationName);
        this.toolchainName = envVars.expand(this.toolchainName);
        this.contents = envVars.expand(this.contents);

        // Check required parameters
        if (Util.isNullOrEmpty(applicationName) || Util.isNullOrEmpty(toolchainName)
                || Util.isNullOrEmpty(contents)) {
            printStream.println("[IBM Cloud DevOps] Missing few required configurations");
            printStream.println("[IBM Cloud DevOps] Error: Failed to upload Build Info.");
            return;
        }

        String environmentName = "";
        if (this.isDeploy || !Util.isNullOrEmpty(this.envName)) {
            environmentName = envVars.expand(this.envName);
        }

        // get IBM cloud environment and token
        String env = getDescriptor().getEnvironment();
        String bluemixToken, buildNumber;
        try {
            buildNumber = Util.isNullOrEmpty(this.buildNumber) ?
                    getBuildNumber(envVars, buildJobName, build, printStream) : envVars.expand(this.buildNumber);
            bluemixToken = getIBMCloudToken(this.credentialsId, this.apikey, this.username, this.password,
                    env, build.getParent(), printStream);

            String dlmsUrl = chooseDLMSUrl(env) + API_PART;
            dlmsUrl = dlmsUrl.replace("{toolchain_id}", URLEncoder.encode(this.toolchainName, "UTF-8").replaceAll("\\+", "%20"));
            dlmsUrl = dlmsUrl.replace("{build_artifact}", URLEncoder.encode(applicationName, "UTF-8").replaceAll("\\+", "%20"));
            dlmsUrl = dlmsUrl.replace("{build_id}", URLEncoder.encode(buildNumber, "UTF-8").replaceAll("\\+", "%20"));
            String link = chooseControlCenterUrl(env) + "deploymentrisk?toolchainId=" + this.toolchainName;
            scanAndUpload(build, workspace, contents, lifecycleStage, bluemixToken, environmentName, dlmsUrl);
            // check to see if we need to upload additional result file
            if (!Util.isNullOrEmpty(additionalContents) && !Util.isNullOrEmpty(additionalLifecycleStage)) {
                scanAndUpload(build, workspace, additionalContents, additionalLifecycleStage, bluemixToken, environmentName, dlmsUrl);
            }

            printStream.println(getMessageWithVar(GO_TO_CONTROL_CENTER, CHECK_TEST_RESULT, link));
        } catch (Exception e) {
            printStream.println(getMessageWithPrefix(GOT_ERRORS) + e.getMessage());
            return;
        }

        // Gate
        // verify if user chooses advanced option to input customized DRA
        if (Util.isNullOrEmpty(policyName)) {
            return;
        }

        String draUrl = chooseDRAUrl(env);
        // get decision response from DRA
        try {
            JsonObject decisionJson = getDecisionFromDRA(bluemixToken, buildNumber, applicationName, environmentName, draUrl);
            if (decisionJson == null) {
                printStream.println(getMessageWithPrefix(NO_DECISION_FOUND));
                return;
            }

            // retrieve the decision id to compose the report link
            String decisionId = String.valueOf(decisionJson.get("decision_id"));
            decisionId = decisionId.replace("\"","");

            // Show Proceed or Failed based on the decision
            String decision = String.valueOf(decisionJson.get("contents").getAsJsonObject().get("proceed"));
            if (decision.equals("true")) {
                decision = "Succeed";
            } else {
                decision = "Failed";
            }

            String cclink = chooseControlCenterUrl(env) + "deploymentrisk?toolchainId=" + this.toolchainName;
            String reportUrl = chooseControlCenterUrl(env) + "decisionreport?toolchainId="
                    + URLEncoder.encode(toolchainName, "UTF-8") + "&reportId=" + decisionId;
            GatePublisherAction action = new GatePublisherAction(reportUrl, cclink, decision, this.policyName, build);
            build.addAction(action);

            // Todo: optimize console output
            printStream.println("************************************");
            printStream.println("Check IBM Cloud DevOps Gate Evaluation report here -" + reportUrl);
            printStream.println("Check IBM Cloud DevOps Deployment Risk Dashboard here -" + cclink);
            // console output for a "fail" decision
            if (decision.equals("Failed")) {
                printStream.println("IBM Cloud DevOps decision to proceed is:  false");
                printStream.println("************************************");
                if (willDisrupt) {
                    Result result = Result.FAILURE;
                    build.setResult(result);
                }
                return;
            }

            // console output for a "proceed" decision
            printStream.println("IBM Cloud DevOps decision to proceed is:  true");
            printStream.println("************************************");

        } catch (IOException e) {
            printStream.print("[IBM Cloud DevOps] Error: " + e.getMessage());
        }

    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    /**
     * Support wildcard for the result file path, scan the path and upload each matching result file to the DLMS
     * @param build
     * @param workspace
     * @param path
     * @param lifecycleStage
     * @param bluemixToken
     * @param environmentName
     * @param dlmsUrl
     * @throws Exception
     */
    public void scanAndUpload(Run build, FilePath workspace, String path, String lifecycleStage, String bluemixToken, String environmentName, String dlmsUrl) throws Exception {
        FilePath[] filePaths = null;

        if (Util.isNullOrEmpty(path)) {
            // if no result file specified, create dummy result based on the build status
            filePaths = new FilePath[]{createDummyFile(build, workspace)};
        } else {
            // remove "./" prefix of the path if it exists
            if (path.startsWith("./")) {
                path = path.substring(2);
            }
            filePaths = workspace.list(path);
        }

        if (filePaths == null || filePaths.length < 1) {
            throw new Exception(getMessage(FAILED_TO_FIND_FILE));
        } else {
            for (FilePath fp : filePaths) {
                // make sure the file path is for file, and copy to the master build folder
                if (!fp.isDirectory()) {
                    FilePath resultFileLocation = new FilePath(new File(root, fp.getName()));
                    fp.copyTo(resultFileLocation);
                }

                //get timestamp
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                TimeZone utc = TimeZone.getTimeZone("UTC");
                dateFormat.setTimeZone(utc);
                String timestamp = dateFormat.format(System.currentTimeMillis());
                String jobUrl;
                if (checkRootUrl(printStream)) {
                    jobUrl = Jenkins.getInstance().getRootUrl() + build.getUrl();
                } else {
                    jobUrl = build.getAbsoluteUrl();
                }

                // upload the result file to DLMS
                sendFormToDLMS(bluemixToken, fp, lifecycleStage, jobUrl, timestamp, environmentName, dlmsUrl);
            }
        }
    }

    /**
     * create a dummy result file following mocha format for some testing which does not generate test report
     * @param build - current build
     * @param workspace - current workspace, if it runs on slave, then it will be the path on slave
     * @return simple test result file
     */
    private FilePath createDummyFile(Run build, FilePath workspace) throws Exception {

        // if user did not specify the result file location, upload the dummy json file
        Gson gson = new Gson();

        //set the passes and failures based on the test status
        int passes, failures;
        Result result = build.getResult();
        if (result != null) {
            if (!result.equals(Result.SUCCESS)) {
                passes = 0;
                failures = 1;
            } else {
                passes = 1;
                failures = 0;
            }
        } else {
            throw new Exception("Failed to get build result");
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        TimeZone utc = TimeZone.getTimeZone("UTC");
        dateFormat.setTimeZone(utc);
        String start = dateFormat.format(build.getStartTimeInMillis());
        long duration = build.getDuration();
        String end = dateFormat.format(build.getStartTimeInMillis() + duration);

        TestResultModel.Stats stats = new TestResultModel.Stats(1, 1, passes, 0, failures, start, end, duration);
        TestResultModel.Test test = new TestResultModel.Test("unknown test", "unknown test", duration, 0, null);
        TestResultModel.Test[] tests = {test};
        String[] emptyArray = {};
        TestResultModel testResultModel = new TestResultModel(stats, tests, emptyArray, emptyArray, emptyArray);

        // create new dummy file
        try {
            FilePath filePath = workspace.child("simpleTest.json");
            filePath.write(gson.toJson(testResultModel), "UTF8");
            return filePath;
        } catch (IOException e) {
            printStream.println("[IBM Cloud DevOps] Failed to create dummy file in current workspace, Exception: " + e.getMessage());
        }

        return null;
    }

    /**
     * Send POST request to DLMS back end with the result file
     * @param bluemixToken
     * @param contents
     * @param lifecycleStage
     * @param jobUrl
     * @param timestamp
     * @param environmentName
     * @param dlmsUrl
     * @throws Exception
     */
    public void sendFormToDLMS(String bluemixToken, FilePath contents, String lifecycleStage, String jobUrl, String timestamp, String environmentName, String dlmsUrl) throws Exception {
        // create http client and post method
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost postMethod = new HttpPost(dlmsUrl);
        postMethod = addProxyInformation(postMethod);
        // build up multi-part forms
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        if (contents == null) {
            throw new Exception(getMessage(FAILED_TO_FIND_FILE));
        } else {
            File file = new File(root, contents.getName());
            FileBody fileBody = new FileBody(file);
            builder.addPart("contents", fileBody);
            builder.addTextBody("test_artifact", file.getName());
            if (this.isDeploy) {
                builder.addTextBody("environment_name", environmentName);
            }

            builder.addTextBody("lifecycle_stage", lifecycleStage);
            builder.addTextBody("url", jobUrl);
            builder.addTextBody("timestamp", timestamp);
            String fileExt = FilenameUtils.getExtension(contents.getName());
            String contentType;
            switch (fileExt) {
                case "json":
                    contentType = CONTENT_TYPE_JSON;
                    break;
                case "xml":
                    contentType = CONTENT_TYPE_XML;
                    break;
                case "info":
                    contentType = CONTENT_TYPE_LCOV;
                    break;
                default:
                    throw new Exception("Error: " + contents.getName() + " is an invalid result file type");
            }

            builder.addTextBody("contents_type", contentType);
            HttpEntity entity = builder.build();
            postMethod.setEntity(entity);
            postMethod.setHeader("Authorization", bluemixToken);
        }

        CloseableHttpResponse response = null;
        try {
            response = httpClient.execute(postMethod);
            // parse the response json body to display detailed info
            String resStr = EntityUtils.toString(response.getEntity());
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                // get 200 response
                printStream.println("[IBM Cloud DevOps] Upload [" + contents.getName() + "] successfully");
            } else if (statusCode == 401 || statusCode == 403) {
                // if gets 401 or 403, it returns html
                throw new Exception(" Failed to upload data, response status " + statusCode
                        + " Please check if you have the access to toolchain " + toolchainName);
            } else {
                JsonParser parser = new JsonParser();
                JsonElement element = parser.parse(resStr);
                JsonObject resJson = element.getAsJsonObject();
                if (resJson != null && resJson.has("message")) {
                    throw new Exception(" Response Status:" + statusCode + ", Reason: " + resJson.get("message"));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Send a request to DRA backend to get a decision
     * @param buildId - build ID, get from Jenkins environment
     * @return - the response decision Json file
     */
    private JsonObject getDecisionFromDRA(String bluemixToken, String buildId, String applicationName, String environmentName, String draUrl) throws IOException {
        // create http client and post method
        CloseableHttpClient httpClient = HttpClients.createDefault();

        String url = draUrl;
        url = url + "/toolchainids/" + toolchainName +
                "/buildartifacts/" + URLEncoder.encode(applicationName, "UTF-8").replaceAll("\\+", "%20") +
                "/builds/" + buildId +
                "/policies/" + URLEncoder.encode(policyName, "UTF-8").replaceAll("\\+", "%20") +
                "/decisions";
        if (this.isDeploy) {
            url = url.concat("?environment_name=" + environmentName);
        }

        HttpPost postMethod = new HttpPost(url);

        postMethod = addProxyInformation(postMethod);
        postMethod.setHeader("Authorization", bluemixToken);
        postMethod.setHeader("Content-Type", CONTENT_TYPE_JSON);

        CloseableHttpResponse response = httpClient.execute(postMethod);
        String resStr = EntityUtils.toString(response.getEntity());

        try {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                // get 200 response
                JsonParser parser = new JsonParser();
                JsonElement element = parser.parse(resStr);
                JsonObject resJson = element.getAsJsonObject();
                printStream.println("[IBM Cloud DevOps] Get decision successfully");
                return resJson;
            } else if (statusCode == 401 || statusCode == 403) {
                // if gets 401 or 403, it returns html
                printStream.println("[IBM Cloud DevOps] Failed to get a decision data, response status " + statusCode
                        + " Please check if you have the access to toolchain " + toolchainName);
            } else {
                JsonParser parser = new JsonParser();
                JsonElement element = parser.parse(resStr);
                JsonObject resJson = element.getAsJsonObject();
                if (resJson != null && resJson.has("message")) {
                    printStream.println("[IBM Cloud DevOps] Failed to get a decision data, response status " + statusCode
                            + ", Reason: " + resJson.get("message"));
                }
            }
        } catch (JsonSyntaxException e) {
            printStream.println("[IBM Cloud DevOps] Invalid Json response, response: " + resStr);
        }
        return null;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override
    public PublishTestImpl getDescriptor() {
        return (PublishTestImpl)super.getDescriptor();
    }

    /**
     * Descriptor for {@link PublishTest}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     *
     * <p>
     * See <tt>src/main/resources/com/ibm/devops/dra/PublishTest/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class PublishTestImpl extends BuildStepDescriptor<Publisher> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */

        /**
         * In order to load the persisted global configuration, you have to
         * call load() in the constructor.
         */
        public PublishTestImpl() {
            super(PublishTest.class);
            load();
        }

        /**
         * Performs on-the-fly validation of the form field 'credentialId'.
         *
         * @param value
         *      This parameter receives the value that the user has typed.
         * @return
         *      Indicates the outcome of the validation. This is sent to the browser.
         *      <p>
         *      Note that returning {@link FormValidation#error(String)} does not
         *      prevent the form from being saved. It just means that a message
         *      will be displayed to the user.
         */

        public FormValidation doCheckApplicationName(@QueryParameter String value)
                throws IOException, ServletException {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckToolchainName(@QueryParameter String value)
                throws IOException, ServletException {
            if (value == null || value.equals("empty")) {
                return FormValidation.errorWithMarkup(getMessageWithPrefix(TOOLCHAIN_ID_IS_REQUIRED));
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckEnvironmentName(@QueryParameter String value)
                throws IOException, ServletException {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckPolicyName(@QueryParameter String value) {
            if (value == null || value.equals("empty")) {
                // Todo: optimize the message
                return FormValidation.errorWithMarkup("Fail to get the policies, please check your username/password or org name and make sure you have created policies for this org and toolchain.");
            }
            return FormValidation.ok();
        }

        public FormValidation doTestConnection(@AncestorInPath ItemGroup context,
                                               @QueryParameter("credentialsId") final String credentialsId) {
            String environment = getEnvironment();
            String targetAPI = chooseTargetAPI(environment);
            String iamAPI = chooseIAMAPI(environment);
            try {
                if (!credentialsId.equals(preCredentials) || Util.isNullOrEmpty(bluemixToken)) {
                    preCredentials = credentialsId;
                    StandardCredentials credentials = findCredentials(credentialsId, context);
                    bluemixToken = getTokenForTestConnection(credentials, iamAPI, targetAPI);
                }
                return FormValidation.okWithMarkup(getMessage(TEST_CONNECTION_SUCCEED));
            } catch (Exception e) {
                e.printStackTrace();
                return FormValidation.error(getMessage(LOGIN_IN_FAIL));
            }
        }

        /**
         * Autocompletion for build job name field
         * @param value - user input for the build job name field
         * @return
         */
        public AutoCompletionCandidates doAutoCompleteBuildJobName(@QueryParameter String value) {
            AutoCompletionCandidates auto = new AutoCompletionCandidates();

            // get all jenkins job
            List<Job> jobs = Jenkins.getInstance().getAllItems(Job.class);
            for (int i = 0; i < jobs.size(); i++) {
                String jobName = jobs.get(i).getName();

                if (jobName.toLowerCase().startsWith(value.toLowerCase())) {
                    auto.add(jobName);
                }
            }
            return auto;
        }

        /**
         * This method is called to populate the credentials list on the Jenkins config page.
         */
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context,
                                                     @QueryParameter("target") final String target) {
            StandardListBoxModel result = new StandardListBoxModel();
            result.includeEmptyValue();
            result.withMatching(CredentialsMatchers.instanceOf(StandardCredentials.class),
                    CredentialsProvider.lookupCredentials(
                            StandardCredentials.class,
                            context,
                            ACL.SYSTEM,
                            URIRequirementBuilder.fromUri(target).build()
                    )
            );
            return result;
        }

        /**
         * This method is called to populate the policy list on the Jenkins config page.
         * @param context
         * @param credentialsId
         * @return
         */
        public ListBoxModel doFillPolicyNameItems(@AncestorInPath ItemGroup context,
                                                  @RelativePath("..") @QueryParameter final String toolchainName,
                                                  @RelativePath("..") @QueryParameter final String credentialsId) {
            String environment = getEnvironment();
            String targetAPI = chooseTargetAPI(environment);
            String iamAPI = chooseIAMAPI(environment);
            try {
                // if user changes to a different credential, need to get a new token
                if (!credentialsId.equals(preCredentials) || Util.isNullOrEmpty(bluemixToken)) {
                    StandardCredentials credentials = findCredentials(credentialsId, context);
                    bluemixToken = getTokenForTestConnection(credentials, iamAPI, targetAPI);
                    preCredentials = credentialsId;
                }
            } catch (Exception e) {
                return new ListBoxModel();
            }
            if(isDebug_mode()){
                LOGGER.info("#######UPLOAD TEST RESULTS : calling getPolicyList#######");
            }
            return getPolicyList(bluemixToken, toolchainName, environment, isDebug_mode());
        }

        /**
         * Required Method
         * This is used to determine if this build step is applicable for your chosen project type. (FreeStyle, MultiConfiguration, Maven)
         * Some plugin build steps might be made to be only available to MultiConfiguration projects.
         *
         * @param aClass The current project
         * @return a boolean indicating whether this build step can be chose given the project type
         */
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            // return FreeStyleProject.class.isAssignableFrom(aClass);
            return true;
        }

        public ListBoxModel doFillLifecycleStageItems(@QueryParameter("lifecycleStage") final String selection) {
            return fillTestType();
        }

        public ListBoxModel doFillAdditionalLifecycleStageItems(@QueryParameter("additionalLifecycleStage") final String selection) {
            return fillTestType();
        }

        /**
         * fill the dropdown list of rule type
         * @return the dropdown list model
         */
        public ListBoxModel fillTestType() {
            ListBoxModel model = new ListBoxModel();
            model.add("Unit Test", "unittest");
            model.add("Functional Verification Test", "fvt");
            model.add("Code Coverage", "code");
            model.add("Static Security Scan", "staticsecurityscan");
            model.add("Dynamic Security Scan", "dynamicsecurityscan");
            return model;
        }

        /**
         * Required Method
         * @return The text to be displayed when selecting your build in the project
         */
        public String getDisplayName() {
            return "Publish test result to IBM Cloud DevOps";
        }

        public String getEnvironment() {
            return getEnv(Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).getConsoleUrl());
        }

        public boolean isDebug_mode() {
            return Jenkins.getInstance().getDescriptorByType(DevOpsGlobalConfiguration.class).isDebug_mode();
        }
    }
}
