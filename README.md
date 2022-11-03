# Design Evaluator


## AWS Deployment

- Registry `923405430231.dkr.ecr.us-east-2.amazonaws.com/comet-algorithm`


### Commands


##### AWS Docker Login

`aws ecr get-login-password --region us-east-2 | docker login --username AWS --password-stdin 923405430231.dkr.ecr.us-east-2.amazonaws.com`


##### Local

`docker-compose build`

`docker-compose up -d`

`dshell comet-algorithm`

`./gradlew run`


##### Dev

`docker build -f DockerfileDev -t apazagab/comet-algorithm:latest .`

`docker run -it -d --entrypoint=/bin/bash --network=comet-network --name=evaluator apazagab/comet-algorithm:latest`


##### Prod

`docker build -f DockerfileProd -t 923405430231.dkr.ecr.us-east-2.amazonaws.com/comet-algorithm:latest .`

`docker push 923405430231.dkr.ecr.us-east-2.amazonaws.com/comet-algorithm:latest`



### Graphql Schema

To generate the schema file necessary for graphql to compile, run the following command.

`gradle downloadApolloSchema`

If you need to change the graphql endpoint, this can be done in the `build.gradle` file

Also can download using apollo from npm

`apollo client:download-schema --header="X-Hasura-Admin-Secret: comet" --endpoint="http://3.133.157.232:8080/v1/graphql"`
