// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package com.aws.twitter.producer.service;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.UUID;

import static com.aws.twitter.producer.AppConfig.TOPIC;
import static java.util.Objects.requireNonNull;

public class KafkaService {

  private final KafkaProducer<String, String> kafkaProducer;

  public KafkaService(KafkaProducer<String, String> kafkaProducer) {
    requireNonNull(kafkaProducer);
    this.kafkaProducer = kafkaProducer;
  }

  public void send(String message) {
    kafkaProducer.send(new ProducerRecord<>(TOPIC, UUID.randomUUID().toString(), message));
  }
}
