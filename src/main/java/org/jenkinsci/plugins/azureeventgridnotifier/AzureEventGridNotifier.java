package org.jenkinsci.plugins.azureeventgridnotifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.model.Run.Artifact;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import net.sf.json.JSONObject;
/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class AzureEventGridNotifier extends Notifier {

    private static final Logger LOG = Logger.getLogger(AzureEventGridNotifier.class.getName());

    private final String topicEndpoint;
    private final String topicKey;
    private final String subjectTemplate;
    private final String messageTemplate;
    private boolean sendNotificationOnStart;
    private boolean notifyOnEveryBuild = true;

    @DataBoundConstructor
    public AzureEventGridNotifier(String topicEndpoint, String topicKey, String subjectTemplate, String messageTemplate, boolean sendNotificationOnStart, boolean notifyOnEveryBuild) {
        super();
        this.topicEndpoint = topicEndpoint;
        this.topicKey = topicKey;
        this.subjectTemplate = subjectTemplate;
        this.messageTemplate = messageTemplate;
        this.sendNotificationOnStart = sendNotificationOnStart;
        this.notifyOnEveryBuild = notifyOnEveryBuild;
    }

    public static AzureEventGridNotifier getNotifier(AbstractProject project) {
        Map<Descriptor<Publisher>, Publisher> map = project.getPublishersList().toMap();
        for (Publisher publisher : map.values()) {
            if (publisher instanceof AzureEventGridNotifier) {
                return (AzureEventGridNotifier) publisher;
            }
        }
        return null;
    }

    public String getTopicEndpoint() {
        return topicEndpoint;
    }

    public String getTopicKey() {
        return topicKey;
    }

    public String getSubjectTemplate() {
        return subjectTemplate;
    }

    public String getMessageTemplate() {
        return messageTemplate;
    }

    public boolean isSendNotificationOnStart() {
        return sendNotificationOnStart;
    }

    public boolean isNotifyOnEveryBuild() {
        return notifyOnEveryBuild;
    }



    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    public void onStarted(AbstractBuild<?, ?> build, TaskListener listener) {
        if (isSendNotificationOnStart()) {
            LOG.info("Prepare Event Grid notification for build started...");
            send(build, listener, BuildPhase.STARTED);
        }
    }

    public void onCompleted(AbstractBuild build, TaskListener listener) {
        boolean isNotifyOnEveryBuild = isNotifyOnEveryBuild();
        boolean previousBuildSuccessful = isPreviousBuildSuccess(build);
        if (isNotifyOnEveryBuild || (!isNotifyOnEveryBuild && !previousBuildSuccessful)) {
            LOG.info("Prepare Event Grid notification for build completed...");
            send(build, listener, BuildPhase.COMPLETED);
        }
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Override
    public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
        onStarted(build, listener);
        return true;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        onCompleted(build, listener);
        return true;
    }

    // ~~ Event Grid specific implementation

    private void send(AbstractBuild build, TaskListener listener, BuildPhase phase) {

        final int length = 100;

        MessageData data = setMessageData(build, listener, phase, messageTemplate);

        Result result = build.getResult();
        if (result == null) {
            return;
        }
        String subject;
        if (StringUtils.isEmpty(subjectTemplate)) {
            subject = truncate(
                    String.format("Build %s: %s",
                            phase == BuildPhase.STARTED ? "STARTED" : result.toString(),
                            build.getFullDisplayName()), length);
        } else {
            subject = Utils.fillTemplate(subjectTemplate, build, listener);
        }


        LOG.info("Setup Event Grid '" + topicEndpoint + "' ...");

        try {
            MessageTemplate message = new MessageTemplate();
            message.setSubject(subject);
            message.setData(data);

            ObjectMapper mapper = new ObjectMapper();
            List<MessageTemplate> messages = new ArrayList<>();
            messages.add(message);
            String body = mapper.writeValueAsString(messages);

            Map<String, String> headers = new HashMap<>();
            headers.put("aeg-sas-key", topicKey);
            String response = Utils.executePost(topicEndpoint, headers, body);
            LOG.info(response);
            listener.getLogger().println("Published Event Grid notification: " + body);
        } catch (Exception e) {
            listener.error("Failed to send Event Grid notification: " + e.getMessage());
        }
    }

    /**
     * Checks to see if the current build result was SUCCESS and the previous build's result
     * was SUCCESS. If this is true then the build state has not changed in a way that should
     * trigger a notification.
     */
    private boolean isPreviousBuildSuccess(AbstractBuild build) {
        return build.getResult() == Result.SUCCESS && findPreviousBuildResult(build) == Result.SUCCESS;
    }

    /**
     * To correctly compute the state change from the previous build to this build,
     * we need to ignore aborted builds, and since we are consulting the earlier
     * result, if the previous build is still running, behave as if this were the
     * first build.
     */
    private Result findPreviousBuildResult(AbstractBuild b) {
        do {
            b = b.getPreviousBuild();
            if (b == null || b.isBuilding()) {
                return null;
            }
        } while ((b.getResult() == Result.ABORTED) || (b.getResult() == Result.NOT_BUILT));
        return b.getResult();
    }

    private String truncate(String s, int toLength) {
        if (s.length() > toLength) {
            return s.substring(0, toLength);
        }
        return s;
    }

    private MessageData setMessageData(AbstractBuild build, TaskListener listener,
                                       BuildPhase phase, String template) {
        MessageData data = new MessageData();
        Result result = build.getResult();
        if (result == null) {
            return data;
        }
        String phaseString = phase == BuildPhase.STARTED ? "STARTED" : result.toString();
        String artifactPaths = artifactPaths(build.getArtifacts());

        HashMap<String, String> envVars = new HashMap<>();
        envVars.put("BUILD_PHASE_NAME", phase.name());
        envVars.put("BUILD_PHASE", phaseString);
        envVars.put("BUILD_DURATION", Long.toString(build.getDuration()));
        envVars.put("BUILD_ARTIFACTS", artifactPaths);

        Utils.setEnvironmentVariables(build, envVars);
        data.setBuildPhase(phase.name());
        data.setArtifactsPaths(artifactPaths);
        data.setBuildResult(phaseString);
        data.setBuildDuration(Long.toString(build.getDuration()));
        data.setCustomMessage(Utils.fillTemplate(template, build, listener));


        return data;
    }

    /**
     * Concatenate build artifact paths into a single new-line separated string.
     */
    private String artifactPaths(List<Artifact> artifacts) {
        List<String> paths = new ArrayList<String>();
        for (Artifact artifact : artifacts) {
            paths.add(artifact.getDisplayPath());
        }
        return StringUtils.join(paths, "\n");
    }


    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public DescriptorImpl() {
            super(AzureEventGridNotifier.class);
            load();
        }

        @Override
        public String getDisplayName() {
            return "Azure Event Grid Notifier";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {

            save();
            return super.configure(req, formData);
        }

    }
}
