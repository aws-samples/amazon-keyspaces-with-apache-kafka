create the s3 bucket 

aws s3api create-bucket --bucket blog-eks-msk-aks 

### Start docker container
docker-compose up -d
### Check ports 22181 and 29092
nc -z localhost 22181
nc -z localhost 29092
### Get kafka logs
docker-compose logs kafka | grep -i started