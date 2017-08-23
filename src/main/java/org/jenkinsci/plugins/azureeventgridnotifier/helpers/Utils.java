/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */
package org.jenkinsci.plugins.azureeventgridnotifier.helpers;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.TaskListener;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    private static final String REGEX = "\\$\\{(.+?)}";

    public static String fillTemplate(String template, AbstractBuild build, TaskListener listener) {
        List<String> tokens = Utils.extractTokens(template);
        HashMap<String, String> replacements = new HashMap<>();
        for (String token
                :
                tokens) {

            String varValue = "";
            try {
                varValue = getEnvVar(build.getEnvironment(listener), token);
            } catch (Exception e) {
                listener.getLogger().println(e.getMessage());
            }
            replacements.put(token, varValue);
        }
        return Utils.tokenizeText(template, replacements);
    }

    public static String executePost(String targetURL, Map<String, String> headers, String urlParameters) {
        HttpURLConnection connection = null;

        try {

            //Create connection
            URL url = new URL(targetURL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded");
            connection.setRequestProperty("Content-Length",
                    Integer.toString(urlParameters.getBytes(StandardCharsets.UTF_8).length));
            connection.setRequestProperty("Content-Language", "en-US");

            for (Map.Entry<String, String> header
                    :
                    headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            connection.setUseCaches(false);
            connection.setDoOutput(true);

            //Send request
            DataOutputStream wr = new DataOutputStream(
                    connection.getOutputStream());
            wr.writeBytes(urlParameters);
            wr.close();

            //Get Response
            InputStream is = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, "utf-8"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                response.append(line);
                response.append('\r');
            }
            rd.close();
            return response.toString();
        } catch (Exception e) {
            e.printStackTrace();
            if (connection != null) {
                InputStream is = connection.getErrorStream();
                BufferedReader rd = null;
                try {
                    rd = new BufferedReader(new InputStreamReader(is, "utf-8"));

                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = rd.readLine()) != null) {
                        response.append(line);
                        response.append('\r');
                    }
                    rd.close();

                    return response.toString();
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

    }

    public static void setEnvironmentVariables(AbstractBuild build, HashMap<String, String> environmentVariables) {

        for (Map.Entry<String, String> var
                :
                environmentVariables.entrySet()) {
            build.addAction(new PublishEnvVarAction(var.getKey(), var.getValue()));
        }
    }

    private static List<String> extractTokens(String text) {
        List<String> tokens = new ArrayList();
        Pattern pattern = Pattern.compile(REGEX);
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group(0).replaceAll("\\$", "").replaceAll("\\{", "").replaceAll("}", ""));
        }
        return tokens;
    }

    private static String tokenizeText(String text, HashMap<String, String> replacements) {
        Pattern pattern = Pattern.compile(REGEX);
        Matcher matcher = pattern.matcher(text);

        StringBuilder builder = new StringBuilder();
        int i = 0;
        while (matcher.find()) {
            String replacement = replacements.get(matcher.group(1));
            builder.append(text.substring(i, matcher.start()));
            if (replacement == null) {
                builder.append(matcher.group(0));
            } else {
                builder.append(replacement);
            }
            i = matcher.end();
        }
        builder.append(text.substring(i, text.length()));
        return builder.toString();
    }

    private static String getEnvVar(EnvVars envVars, String var) {
        String env = System.getenv(var);
        if (envVars != null && (env == null || env.equals(""))) {
            env = envVars.get(var);
        }
        return env;
    }

    public String getREGEX() {
        return REGEX;
    }

}
