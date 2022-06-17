## Run Apache Kafka locally
### Start docker container
docker-compose up -d
### Check ports 22181 and 29092
nc -z localhost 22181
nc -z localhost 29092
### Get kafka logs
docker-compose logs kafka | grep -i started