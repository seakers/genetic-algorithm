########################
### Build Containers ###
########################


## 1. SystemArchitectureProblems ##
FROM maven:3-jdk-11-slim AS BUILD_TOOL
WORKDIR /repos
RUN apt-get update -y
RUN apt-get upgrade -y
RUN apt-get install git -y
RUN git clone https://github.com/seakers/SystemArchitectureProblems.git

WORKDIR /repos/SystemArchitectureProblems
RUN git fetch && git checkout jdk-11
RUN mvn install




#######################
### Final Container ###
#######################


FROM amazoncorretto:11
COPY --from=BUILD_TOOL /root/.m2 /root/.m2


### Prod Variables ###
#ENV QUEUE_URL http://localstack:4576
#ENV REGION US_EAST_2
#ENV MESSAGE_RETRIEVAL_SIZE 1
#ENV MESSAGE_QUERY_TIMEOUT 5
#ENV RABBITMQ_HOST rabbitmq
#ENV DEV True
#ENV DEBUG True
#ENV AWS_STACK_ENDPOINT http://localstack:4576


# -- DEPS --
WORKDIR /installs

RUN yum update -y && \
    yum upgrade -y && \
    yum install git wget unzip tar -y


# -- GRAPHQL SCHEMA --
WORKDIR /app
# RUN gradle generateApolloSources
# CMD gradle run


















