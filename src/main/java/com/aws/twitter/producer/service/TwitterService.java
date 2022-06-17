// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.aws.twitter.producer.service;

import com.aws.twitter.models.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.aws.twitter.producer.AppConfig.TWITTER_API_URL;
import static java.util.Objects.requireNonNull;

public class TwitterService {
    private static final Logger LOG = LoggerFactory.getLogger(TwitterService.class);
    private static Pattern PATTERN = Pattern.compile("^[a-zA-Z0-9]*$_@#");
    private static ObjectMapper objectMapper = new ObjectMapper();
    private static Pattern PATTERN_RT = Pattern.compile("@(.*?):");
    private final KafkaService kafkaService;
    private final HttpClient httpClient;

    public TwitterService(KafkaService kafkaService, HttpClient httpClient) {
        requireNonNull(kafkaService);
        requireNonNull(httpClient);
        this.kafkaService = kafkaService;
        this.httpClient = httpClient;
    }

    public static boolean isAlphaNumeric(String s) {
        return PATTERN.matcher(s).find();
    }

    /*
     * This method calls the sample stream endpoint and streams Tweets from it
     * */
    public void connectStream(String authToken) throws IOException, URISyntaxException {

        var response = this.httpClient.execute(httpGet(authToken, uri()));
        var entity = response.getEntity();

        if (null != entity) {
            var reader = new BufferedReader(new InputStreamReader((entity.getContent())));
            var line = reader.readLine();
            while (null != line) {
                filterAndSendTweets(line);
                line = reader.readLine();
            }
        } else LOG.warn("Entity is Null");
    }

    private HttpGet httpGet(String bearerToken, URIBuilder uriBuilder) throws URISyntaxException {
        var httpGet = new HttpGet(uriBuilder.build());
        httpGet.setHeader("Authorization", String.format("Bearer %s", bearerToken));
        httpGet.setHeader("Content-Type", "application/json");
        return httpGet;
    }

    private URIBuilder uri() throws URISyntaxException {
        var uriBuilder = new URIBuilder(TWITTER_API_URL);
        List<NameValuePair> queryParameters = new ArrayList<>();
        queryParameters.add(new BasicNameValuePair("tweet.fields", "entities,lang,created_at"));
        queryParameters.add(new BasicNameValuePair("user.fields", "location,username"));
        queryParameters.add(new BasicNameValuePair("place.fields", "country,geo"));
        uriBuilder.addParameters(queryParameters);
        return uriBuilder;
    }

    private void filterAndSendTweets(String line) throws JsonProcessingException {
        var str = line.replace("'", "\\\\u0027");
        if (!str.isEmpty()) {
            JsonNode jsonNode = objectMapper.readTree(str);
            boolean isEntityEmpty = jsonNode.get("data").get("entities").isEmpty();
            var id = jsonNode.get("data").get("id").textValue();
            var lang = jsonNode.get("data").get("lang").textValue();
            var text = jsonNode.get("data").get("text").textValue();
            var date = jsonNode.get("data").get("created_at").textValue();
            if (!isEntityEmpty && jsonNode.get("data").get("entities").get("hashtags") != null &&
                    jsonNode.get("data").get("entities").get("mentions") != null) {
                jsonNode.get("data").get("entities").get("hashtags").forEach(x ->
                {
                  Matcher matcher = PATTERN_RT.matcher(text);
                  var username = "";
                  if (matcher.find()) {
                    username = matcher.group(1);
                  }
                  Message message = new Message(date, id, lang, text, username, x.get("tag").asText());
                  String serializedMessage = null;
                  try {
                    serializedMessage = objectMapper.writeValueAsString(message);
                  } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                  }
                  if (!message.getUsername().isBlank() || !message.getUsername().isEmpty()) {
                    LOG.debug("{}", message);
                    this.kafkaService.send(serializedMessage);
                  }
                });
            }

        }
    }
}
