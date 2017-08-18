package org.jenkinsci.plugins.azureeventgridnotifier;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;


class MessageData {
    private String buildPhase;
    private String artifactsPaths;
    private String buildResult;
    private String buildDuration;
    private String customMessage;

    public String getBuildPhase() {
        return buildPhase;
    }

    public void setBuildPhase(String buildPhase) {
        this.buildPhase = buildPhase;
    }

    public String getArtifactsPaths() {
        return artifactsPaths;
    }

    public void setArtifactsPaths(String artifactsPaths) {
        this.artifactsPaths = artifactsPaths;
    }

    public String getBuildResult() {
        return buildResult;
    }

    public void setBuildResult(String buildResult) {
        this.buildResult = buildResult;
    }

    public String getBuildDuration() {
        return buildDuration;
    }

    public void setBuildDuration(String buildDuration) {
        this.buildDuration = buildDuration;
    }

    public String getCustomMessage() {
        return customMessage;
    }

    public void setCustomMessage(String customMessage) {
        this.customMessage = customMessage;
    }
}

public class MessageTemplate {

    private String id;
    private String eventType;
    private String subject;
    private String eventTime;
    private MessageData data;

    public MessageTemplate() {
        int randomId = ThreadLocalRandom.current().nextInt();
        id = String.valueOf(randomId);
        eventType = "recordInserted";
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        eventTime = dateFormat.format(new Date()); //"2017-08-10T21:03:07+00:00";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getEventTime() {
        return eventTime;
    }

    public void setEventTime(String eventTime) {
        this.eventTime = eventTime;
    }

    public MessageData getData() {
        return data;
    }

    public void setData(MessageData data) {
        this.data = data;
    }
}
