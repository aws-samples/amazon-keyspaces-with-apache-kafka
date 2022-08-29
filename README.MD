## Prerequisite
1.	Prepare a bearer token associated with your twitter app. To create a developer account, see a section ["Get started with the Twitter developer platform"](https://developer.twitter.com/en/docs/platform-overview).
2.	[aws-cli](https://aws.amazon.com/cli/) version 2. You will use aws-cli with the profile and credentials for your AWS account. The solution requires [AWS credentials](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-files.html) in order to access AWS services.
3.	You need to use Docker to package the application as portable container image to publish to Amazon Elastic Container Registry (Amazon ECR).
4.	kubectl allows you to run commands against [Kubernetes](https://kubernetes.io/) clusters.
5.  clone the repository 

## Deploy the stack using CloudFormation

***Manaully set the AWS region to use create the stack***
```
export AWS_REGION=us-east-1
```
***OR use the configured region, to check current region***
```
export AWS_REGION=$(aws configure get region)
```

**First use aws-cli to create s3 bucket to store cfn template, custom plugins which makes the post deployment cleanup easier**

create the s3 bucket 
```
aws s3api create-bucket --bucket blog-eks-msk-aks 
```
**Switch to Templates folder deploy the cfn-eks-msk-aks.yml, the template creates the AWS resources. The cfn requires you to pass parameters for ssh-key and ip address range to access the kafka client instance from**

```
cd Templates

aws cloudformation deploy --template-file cfn-eks-msk-aks.yml --stack-name eks-msk-aks-sink-stack --parameter-overrides KeyName= < SSH-KEY-Name >   SSHLocation=< ip-address > --tags aws-blog=eks-msk-aks-sink --s3-bucket blog-eks-msk-aks  --capabilities CAPABILITY_NAMED_IAM  --on-failure ROLLBACK
```
***KeyName: ssh keyname to be used for kafkaclient Instance***
***SSHLocation: Ip address range which allow ssh access (default is 0.0.0.0/0)***

**Resource creation could take up to 25-30 mins,once the CloudFormation template is deployed. Run the following command to get the output of the stack created and save it in stack_resources_output file**
```
aws cloudformation describe-stacks --stack-name eks-msk-aks-sink-stack --query "Stacks[0].Outputs[*].[OutputKey,OutputValue]" --output text > stack_resources_output
```

**Based on stack_resources_output the _update-param_ script updates the parameters in template files used for pod and connector deployment**
```
sh update-param.sh
```

**To add environment variables for your AWS Region and the account ID from your AWS configuration.**
```
export AWS_REGION=$(aws configure get region)
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
```

## Build and Deploy Event source application on EKS

**Run the following command to log in to ECR.**
```
cd ..
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com
```

**Run the following command to build docker image, add a tag, and push the container image to the ECR**
```
mvn package
docker-compose build
docker tag amazon-keyspaces-with-apache-kafka:latest  $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/amazon-keyspaces-with-apache-kafka:latest
docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/amazon-keyspaces-with-apache-kafka:latest
```

**aws eks update-kubeconfig command updates the default kubeconfig file to use eks-twitter-cluster as the current context**

```
aws eks update-kubeconfig --name eks-twitter-cluster 
```
**Run the following kubectl commands to create namespace, verify if namespace is created**
```
kubectl create namespace eks-msk-twitter
kubectl get namespaces

```

**_SWITCH to the Templates folder_**
```
cd Templates
```
**you need to replace value of bearer token in the _twitter-cfn-app-deployment.yaml_ template for twitter access, deploy the pods to eks-msk-twitter namespace**

**_<< Bearer Token >>_, Run the following commands to deploy the pods and verify the pod status**

```
kubectl apply -f twitter-cfn-app-deployment.yaml
kubectl get pods -n=eks-msk-twitter -o wide
```

**Replace the username and password to connect to Amazon Keyspaces in _kafka-keyspaces-connector.json_ file  "auth.username": "< keyspaces-user-at > ", "auth.password": “< password >",**


**Run the following command to deploy the connector**
```
aws kafkaconnect create-connector --cli-input-json file://kafka-keyspaces-connector.json
```

To follow best practices for Amazon Keyspaces you can use the cqlsh-expansion library, which extends the existing cqlsh library with additional helpers or the Amazon Keyspaces CQL console. Use the following command to connect to Amazon Keyspaces using the cqlsh-expansion library with the SigV4AuthProvider for short term credentials

To install and confgure the cqlsh-expansion utility
```
pip install --user cqlsh-expansion
cqlsh-expansion.init
```

To connect to Amazon Keyspaces to check the data 

```
cqlsh-expansion cassandra.$AWS_REGION.amazonaws.com 9142 --ssl --auth-provider "SigV4AuthProvider"
```

### CQL Queries to get data from Amazon Keyspaces 
CQL Query to get sample data from tweet_by_tweet_id and tweet_by_user tables

```
SELECT * FROM aws_blog. tweet_by_tweet_id limit 10;
SELECT * FROM aws_blog. tweet_by_user limit 10;
```

CQL Query to get tweet specific data

```
SELECT * FROM aws_blog. tweet_by_tweet_id where tweet_id=1554855502932312066;
```

CQL Query to get user specific tweet data

```
SELECT * FROM aws_blog. tweet_by_user where username = ‘awscloud’;
```

## DELETE the stack

**Run the following command to delete the stack**
```
./delete-stack.sh

```