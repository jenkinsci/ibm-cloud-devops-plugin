package com.ibm.devops.connect.Status;

import net.sf.json.JSONObject;
import java.util.Set;
import hudson.EnvVars;
import hudson.model.TaskListener;
import hudson.FilePath;
import hudson.plugins.git.util.Build;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import hudson.plugins.git.Revision;
import org.eclipse.jgit.revwalk.RevCommit;
import hudson.plugins.git.util.RevCommitRepositoryCallback;

import java.io.IOException;
import java.lang.InterruptedException;

public class SourceData {
    public static final Logger log = LoggerFactory.getLogger(SourceData.class);

    private String branch;
    private String revision;
    private String scmName;
    private String type;
    private String shortMessage;
    private String fullMessage;
    private Set<String> remoteUrls;

    public SourceData(String branch, String revision, String type) {
        this.branch = branch;
        this.revision = revision;
        this.type = type;
    }

    public void setBranch (String branch) {
        this.branch = branch;
    }

    public void setRevision (String revision) {
        this.revision = revision;
    }

    public void setScmName(String scmName) {
        this.scmName = scmName;
    }

    public void setType (String type) {
        this.type = type;
    }

    public void setRemoteUrls (Set<String> remoteUrls) {
        this.remoteUrls = remoteUrls;
    }

    public JSONObject toJson() {
        JSONObject result = new JSONObject();

        result.put("branch", branch);
        result.put("revision", revision);
        result.put("scmName", scmName);
        result.put("type", type);
        result.put("fullMessage", fullMessage);
        result.put("shortMessage", shortMessage);
        result.put("type", type);
        if(remoteUrls != null) {
            result.put("remoteUrls", remoteUrls.toArray());
        }

        return result;
    }

    public void populateCommitMessage(TaskListener listener, EnvVars envVars, FilePath workspace, Build gitBuild) {
        try {
            Git git = Git.with(listener, envVars);
            if (workspace != null) {
                git = git.in(workspace);
            }

            GitClient gitClient = git.getClient();
            RevCommit commit = gitClient.withRepository(new RevCommitRepositoryCallback(gitBuild));

            this.shortMessage = commit.getShortMessage();
            this.fullMessage = commit.getFullMessage();

        } catch (IOException ioEx) {
            log.warn("IOException thrown while trying to retrieve commit message in populateCommitMessage: " + ioEx);
        } catch (InterruptedException intEx) {
            log.warn("InterruptException thrown while trying to retrieve commit message in populateCommitMessage: " + intEx);
        }
    }
}