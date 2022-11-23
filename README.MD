# Amazon Keyspaces with Apache Kafka

This repository contains hands on content how to build a data pipeline to ingest real time data using managed open-source compatible services such as Amazon Elastic Kubernetes Service (EKS), Amazon Managed Streaming for Apache Kafka (MSK), and Amazon Keyspaces (for Apache Cassandra).
Apache Kafka and Cassandra share distributed core capabilities like high availability, scalability, and throughput that makes them a good solution for large scale processing applications like IoT data, user metadata, trade monitoring, and route optimization.
This data pipeline can consume a sample stream from Twitter API which streams 1% of all the tweets in realtime as a data source, parse the tweets, metadata, and publish the parsed data to a Kafka topic. Kafka works as a distributed queue as well as a buffer layer to transport messages. 
MSK Connect consumes these messages from kafka topic and writes them to Amazon Keyspaces tables.

The solution uses EKS to deploy containerized Twitter Event source application, the containerized application consumes, and a stream of tweets from Twitter API, parse the tweets (discards tweets that don’t have a hashtag), extract tweet metadata (created at, lang etc.), 
publishes these messages to Kafka topic twitter_input with desired fields using Kafka producer API. You will use the MSK Connect to ingest data from the twitter_input topic to Amazon Keyspaces. 

![Solution](static/architecture.png)

[Amazon Keyspaces (for Apache Cassandra)](https://aws.amazon.com/keyspaces/) is a scalable, highly available, and managed Apache Cassandra compatible database service. With Amazon Keyspaces, you can run your Cassandra workloads on AWS using the same Cassandra application code and developer tools that you use today. 
Amazon Keyspaces removes the administrative overhead of managing Cassandra. With Amazon Keyspaces you pay only for the resources you use. Unlike Apache Cassandra the tables in Amazon Keyspaces are independent, isolated resources that can scale automatically in response to application traffic which makes it easier 
to run workloads with variable traffic patterns rather than traditional approach of over provisioning Cassandra clusters for peak traffic. 

## Prerequisite
1.	prepare a bearer token associated with your twitter app. Create a developer account, see a section ["Get started with the Twitter developer platform"](https://developer.twitter.com/en/docs/platform-overview);
2.	[aws-cli](https://aws.amazon.com/cli/) version 2. You will use aws-cli with the profile and credentials for your AWS account. The solution requires [AWS credentials](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-files.html) in order to access AWS services;
3.	you need to use Docker to package the application as portable container image to publish to Amazon Elastic Container Registry (Amazon ECR);
4.	kubectl allows you to run commands against [Kubernetes](https://kubernetes.io/) clusters;
5.  clone the repository. 

## Deploy the stack using CloudFormation

Manually set the AWS region to use create the stack
```
export AWS_REGION=us-east-1
```
or use the configured region, to check current region
```
export AWS_REGION=$(aws configure get region)
```
Add environment variables for your AWS Region and the account ID from your AWS configuration
```
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
```
First use aws-cli to create s3 bucket to store the cfn template, custom plugins which makes the post deployment cleanup easier
```
aws s3api create-bucket --bucket blog-eks-msk-aks-$AWS_ACCOUNT_ID
```
Switch to `Templates` folder deploy the cfn-eks-msk-aks.yml, the template creates the AWS resources. 
Deploying the CFN does requires you to pass parameters for SSH key (pass the key pair that already exists in the aws account else need to create a new one) 
and IP address range to access the Kafka client instance.

```
cd Templates
aws cloudformation deploy --template-file cfn-eks-msk-aks.yml --stack-name eks-msk-aks-sink-stack --parameter-overrides KeyName= < SSH-KEY-Name >   SSHLocation=< ip-address > --tags aws-blog=eks-msk-aks-sink --s3-bucket blog-eks-msk-aks-$AWS_ACCOUNT_ID  --capabilities CAPABILITY_NAMED_IAM  --on-failure ROLLBACK
```
KeyName is ssh key name to be used for the kafkaclient instance. SSHLocation is ip address range which allow ssh access (default is 0.0.0.0/0).

The CloudFormation stack will create the following resources:

1. a virtual private cloud (VPC) with two public and three private subnets. Internet gateway, NAT gateways, configuring route tables to route traffic, necessary security groups, and IAM roles need for the execution;
2. a VPC endpoint for Amazon Keyspaces. the VPC endpoint enable private communication between your VPC and Amazon Keyspaces;
3. a KMS Encryption key with SYMMETRIC_DEFAULT KeySpec, Amazon Keyspaces user and hashtag tables along with aws_blog keyspace;
4. an Amazon ECR Repository to publish docker container images which you build in later stage of the blog;
5. an EKS cluster into which the application tasks will be deployed, Node group to deploy the tasks on EC2 instances for the cluster;
6.an MSK cluster in private subnets of VPC, along with kafka client instance to access MSK cluster to create Kafka topic.

Run the following command to get the output of the stack created and save it in stack_resources_output file
```
aws cloudformation describe-stacks --stack-name eks-msk-aks-sink-stack --query "Stacks[0].Outputs[*].[OutputKey,OutputValue]" --output text > stack_resources_output
```

Based on stack_resources_output the helper _update-param_ script updates the parameters in template files used for pod and connector deployment
```
sh update-param.sh
```
So far you have deployed the stack using CFN template. Used the output of stack to update parameters for pod and connector deployment templates
## Build and Deploy Event source application on EKS

Run the following command to log in in the ECR registry
```
cd ..
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com
```

Run the following command to build docker container image, add a tag, and push the container image to the ECR registry
```
mvn package
docker-compose build
docker tag amazon-keyspaces-with-apache-kafka:latest  $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/amazon-keyspaces-with-apache-kafka:latest
docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/amazon-keyspaces-with-apache-kafka:latest
```

aws eks update-kubeconfig command updates the default kubeconfig file to use eks-twitter-cluster as the current context
```
aws eks update-kubeconfig --name eks-twitter-cluster 
```
Run the following kubectl commands to create namespace, verify if namespace is created
```
kubectl create namespace eks-msk-twitter
kubectl get namespaces
```

Switch to `Templates` folder
```
cd Templates
```
You need to replace value of bearer token in the _twitter-cfn-app-deployment.yaml_ template for twitter access, deploy the pods to eks-msk-twitter namespace

_<< Bearer Token >>_, Run the following commands to deploy the pods and verify the pod status

```
kubectl apply -f twitter-cfn-app-deployment.yaml
kubectl get pods -n=eks-msk-twitter -o wide
```

Now the application is deployed, and it starts reading sample stream data, parse tweet data and write it to the kafka topic, next step would be to create the msk connector to sink data from the kafka topic to Amazon keyspaces
The custom-plugin is downloaded and packaged as a part of kafka-clinet instance creation, you need to deploy the connector, run the following commands, from the `Templates` folder where there is the saved kafka-keyspaces-connector JSON file to create connector. 
In order to connect to Amazon Keyspaces with many open-source tooling that predates Amazon Keyspaces, you will need to generate the service-specific credentials. Service-specific credentials enable IAM users to authenticate with Amazon Keyspaces via a username and password. 
These credentials can be used with CQLSH and other Apache Cassandra applications that use user/password mechanism. The credentials cannot be used to access other AWS services
Make changes to connector configuration in kafka-keyspaces-connector.json file

Replace the username and password to connect to Amazon Keyspaces in _kafka-keyspaces-connector.json_ file  "auth.username": "< keyspaces-user-at > ", "auth.password": “< password >",
Run the following command to deploy the connector
```
aws kafkaconnect create-connector --cli-input-json file://kafka-keyspaces-connector.json
```
After deploy is completed the sink connector uses interface VPC endpoints to connect and write messages from the kafka topic to Amazon Keyspaces tables
Now the data pipeline is complete, you can use the cqlsh-expansion library which extends the existing cqlsh library with additional helpers, or the Amazon Keyspaces CQL console the read the data from Amazon Keyspaces tables

Use the following command to connect to Amazon Keyspaces using the cqlsh-expansion library with the SigV4AuthProvider for short term credentials

To install and confgure the cqlsh-expansion utility
```
pip install --user cqlsh-expansion
cqlsh-expansion.init
```

To connect to Amazon Keyspaces to check the data 

```
cqlsh-expansion cassandra.$AWS_REGION.amazonaws.com 9142 --ssl --auth-provider "SigV4AuthProvider"
```
### Sample CQL Queries to get data from Amazon Keyspaces 
CQL Query to get sample data from tweet_by_tweet_id and tweet_by_user tables

```
SELECT * FROM aws_blog.tweet_by_tweet_id limit 10;
SELECT * FROM aws_blog.tweet_by_user limit 10;
```

CQL Query to get tweet specific data

```
SELECT * FROM aws_blog.tweet_by_tweet_id where tweet_id=1554855502932312066;
```

CQL Query to get user specific tweet data

```
SELECT * FROM aws_blog.tweet_by_user where username = ‘awscloud’;
```
## Delete the stack
Run delete-stack bash script to delete all the resources, created as part of this blog, you can monitor the stack deletion status through console or using awscli

Run the following command to delete the stack
```
./delete-stack.sh

```
## Summary
Now you can build your data pipeline to ingest real time data using managed open source compatible services EKS, MSK, and Amazon Keyspaces. 
These managed services allow developers to offload the building, maintaining, and expertise in operating the underlying infrastructure, allowing focusing on delivering differentiating features.
We encourage you to explore adding security features and adopting security best practices according to your needs and potential company standards.
