#!/bin/bash

ZK=$(aws kafka list-clusters --region $AWS_REGION --output text | grep arn | grep CLUSTERINFOLIST | grep aws-blog-MSKCluster | awk '{print $9}')

#echo $ZK

subnet_one=$(cat stack_resources_output | grep PrivateSubnetOne | awk '{print $2}')
subnet_two=$(cat stack_resources_output | grep PrivateSubnetTwo | awk '{print $2}')
subnet_three=$(cat stack_resources_output | grep PrivateSubnetThree | awk '{print $2}')
kafka_sg=$(cat stack_resources_output | grep MSKSecurityGroupID | awk '{print $2}')
ecr_rep=$(cat stack_resources_output | grep ECRRepository | awk '{print $2}')

connect_arn=$(aws kafkaconnect --region $AWS_REGION list-custom-plugins | grep customPluginArn | grep kafka-keyspaces-sink-plugin | awk '{print $2}')
kafkarn=$(cat stack_resources_output | grep BlogMSKClusterArn | awk '{print $2}')
kafka_bootstrap_str="aws kafka get-bootstrap-brokers --cluster-arn $kafkarn"
kafka_bootstrap_brokers=$($kafka_bootstrap_str | grep 'BootstrapBrokerString' | awk '{print $2}'| head -n 1 )
kafka_bootstrap_brokers_iam=$($kafka_bootstrap_str | grep 'BootstrapBrokerString' | awk '{print $2}' | tr -d '"' | sed 's/,$//' | tail -n 1 )
msk_role_arn=$(cat stack_resources_output | grep MSKconnectRoleID | awk '{print $2}')

sed -i -e "s/subnet1/$subnet_one/" kafka-keyspaces-connector.json
sed -i -e "s/subnet2/$subnet_two/" kafka-keyspaces-connector.json
sed -i -e "s/subnet3/$subnet_three/" kafka-keyspaces-connector.json
sed -i -e "s#plugin_arn#$connect_arn#g" kafka-keyspaces-connector.json
sed -i -e "s#bootstrap_brokers#$kafka_bootstrap_brokers#g" kafka-keyspaces-connector.json
sed -i -e "s/kafka_sg/$kafka_sg/" kafka-keyspaces-connector.json
sed -i -e "s#msk_role#$msk_role_arn#" kafka-keyspaces-connector.json
sed -i -e "s/keyspaces_dc/$AWS_REGION/" kafka-keyspaces-connector.json
sed -i -e "s/cntpt_dc/$AWS_REGION/" kafka-keyspaces-connector.json


sed -i -e "s#bootstrap_brokers#$kafka_bootstrap_brokers_iam#g" twitter-cfn-app-deployment.yaml
sed -i -e "s#ECR_Rep#$ecr_rep#g" twitter-cfn-app-deployment.yaml
