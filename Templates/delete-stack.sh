#!/bin/bash

# Get the parameters from stack_resource_output file
ec2_id=$(cat stack_resources_output | grep kafkaclinetinstance | awk '{print $2}')
vpc_id=$(cat stack_resources_output | grep keyspacesVPCId | awk '{print $2}')
 
# Delete kafka client instance
delete_ec2=$(aws ec2 terminate-instances --instance-ids "$ec2_id")

# Delete Amazon Keyspaces Vpc endpoint
delete_vpc_endpnts=$(aws ec2 delete-vpc-endpoints --vpc-endpoint-ids "$vpc_id")

aws eks delete-nodegroup --cluster-name  eks-twitter-cluster  --nodegroup-name eks-compute
aws ecr batch-delete-image --repository-name amazon-keyspaces-with-apache-kafka --image-ids imageTag=latest

# Delete MSK connect plugin and connector
plugin_arn=$(aws kafkaconnect --region $AWS_REGION list-custom-plugins | grep customPluginArn | grep kafka-keyspaces-sink-plugin | awk '{print $2}' | tr -d '"' | tr -d ',')
connect_arn=$(aws kafkaconnect --region $AWS_REGION list-connectors | grep kafka-AmazonKeyspaces-sink-connector | grep connectorArn | awk '{print $2}' | tr -d '"' | tr -d ',')

# if $connect_arn
delete_connector=$(aws kafkaconnect delete-connector --connector-arn "$connect_arn")
# fi

# if $plugin_arn
# fi

ec2_status_query="aws ec2 describe-instances --query "Reservations[*].Instances[*].InstanceId" --filters "Name=instance-state-name,Values=terminated" --region $AWS_REGION --output text"
eks_status_query="aws eks  describe-nodegroup --cluster-name  eks-twitter-cluster  --nodegroup-name eks-compute"

# Get IAM Roles 
ec2_role=$(cat stack_resources_output | grep Ec2Rolename | awk '{print $2}')
eks_role=$(cat stack_resources_output | grep EKSRolename | awk '{print $2}')
msk_role=$(cat stack_resources_output | grep MSKconnectRolename | awk '{print $2}')
iam_roles=($msk_role $eks_role $ec2_role)

# Detach policy and delete roles once ec2 instances are terminated
while [ true ]
do
  ec2_status=$($ec2_status_query | grep $ec2_id)
  eks_status=$($eks_status_query 2> /dev/null)
  return_status=$?
  # echo $return_status
  # echo $ec2_status_query
  # echo $ec2_id
  # echo $ec2_status
  if [ $return_status != 0 ] && [ "$ec2_status" = "$ec2_id" ]; then
     echo "Deleting Roles"
     detach_profile_ec2_role=$(aws iam remove-role-from-instance-profile --instance-profile-name EC2MSKProfile --role-name "$ec2_role")
     delete_inline_policy_ec2=$(aws iam delete-role-policy --role-name "$ec2_role" --policy-name 'mskconnect')
     delete_inline_policy_msk=$(aws iam delete-role-policy --role-name "$msk_role" --policy-name 'mskconnect')
     for role in ${iam_roles[@]}
     do 
        managed_policies=$(aws iam list-attached-role-policies --role-name "$role" | grep PolicyArn | awk '{print $2}')
        for mng_policy in ${managed_policies[@]}
        do
          policy=$(echo $mng_policy |  tr -d '"')
          aws iam detach-role-policy --role-name "$role" --policy-arn "$policy"
        done
     done
     delete_ec2_role=$(aws iam delete-role --region $AWS_REGION --role-name "$ec2_role")
     delete_eks_role=$(aws iam delete-role --region $AWS_REGION --role-name "$eks_role")
     delete_msk_role=$(aws iam delete-role --region $AWS_REGION --role-name "$msk_role")
    break
  fi
  echo "Waiting for instance deletion .."
  sleep 10

done

aws s3 rm s3://blog-eks-msk-aks --recursive
delete_plugin=$(aws kafkaconnect delete-custom-plugin --custom-plugin-arn "$plugin_arn")

aws cloudformation delete-stack --stack-name eks-msk-aks-sink-stack


