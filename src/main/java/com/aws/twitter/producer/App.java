// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.aws.twitter.producer;

import com.aws.twitter.producer.service.KafkaService;
import com.aws.twitter.producer.service.TwitterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;

import static com.aws.twitter.producer.AppConfig.BEARER_TOKEN;

public class App {
  private static final Logger logger = LoggerFactory.getLogger(App.class);

  public static void main(String[] args) throws IOException, URISyntaxException {

    var configs = new AppConfig();
    var kafkaService = new KafkaService(configs.kafkaProducer());
    var twitterService = new TwitterService(kafkaService, configs.httpClient());
    if (null != BEARER_TOKEN) {
      twitterService.connectStream(BEARER_TOKEN);
    } else {
      logger.error(
          "There was a problem getting you bearer token. Please make sure you set the BEARER_TOKEN environment variable");
    }
  }
}
