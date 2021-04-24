package sqs;

import algorithm.Algorithm;
import com.apollographql.apollo.ApolloClient;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import okhttp3.OkHttpClient;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.moeaframework.core.Solution;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest;
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.StopTaskRequest;
import software.amazon.awssdk.services.ecs.model.StopTaskResponse;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;
import software.amazon.awssdk.regions.Region;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class Consumer implements Runnable {

    private enum State {
        WAITING_FOR_USER, WAITING_FOR_ACK, READY
    }

    private SqsClient sqsClient;
    private String    requestQueueUrl;
    private String    responseQueueUrl;
    private String    userRequestQueueUrl;
    private String    userResponseQueueUrl;
    private String    vassarRequestQueueUrl;
    private String    deadLetterQueueArn;
    private String    apolloUrl;
    private boolean   debug;
    private boolean   running;
    private int       messageRetrievalSize;
    private int       messageQueryTimeout;
    private Map<String, Thread> algorithms;
    private Region    region;
    private String    awsStackEndpoint;
    private State     currentState = State.WAITING_FOR_USER;
    private String    uuid = UUID.randomUUID().toString();
    private long      lastPingTime = System.currentTimeMillis();
    private int       userId;

    public static class Builder{

        private SqsClient sqsClient;
        private String    requestQueueUrl;
        private String    responseQueueUrl;
        private String    apolloUrl;
        private boolean   debug;
        private int       messageRetrievalSize;
        private int       messageQueryTimeout;
        private Region    region;
        private String    awsStackEndpoint;

        public Builder(SqsClient sqsClient){
            this.sqsClient = sqsClient;
        }

        public Builder setRequestQueueUrl(String requestQueueUrl) {
            this.requestQueueUrl = requestQueueUrl;
            return this;
        }

        public Builder setResponseQueueUrl(String responseQueueUrl) {
            this.responseQueueUrl = responseQueueUrl;
            return this;
        }

        public Builder setAwsStackEndpoint(String aws_stack_endpoint){
            this.awsStackEndpoint = aws_stack_endpoint;
            return this;
        }

        public Builder setRegion(Region region){
            this.region = region;
            return this;
        }


        public Builder debug(boolean debug){
            this.debug = debug;
            return this;
        }

        public Builder setMessageRetrievalSize(int messageRetrievalSize){
            this.messageRetrievalSize = messageRetrievalSize;
            return this;
        }

        public Builder setApolloUrl(String apolloUrl){
            this.apolloUrl = apolloUrl;
            return this;
        }

        public Builder setMessageQueryTimeout(int messageQueryTimeout){
            this.messageQueryTimeout = messageQueryTimeout;
            return this;
        }


        public Consumer build(){
            Consumer build = new Consumer();

            build.sqsClient      = this.sqsClient;
            build.apolloUrl      = this.apolloUrl;
            build.requestQueueUrl  = this.requestQueueUrl;
            build.responseQueueUrl = this.responseQueueUrl;
            build.debug            = this.debug;
            build.messageRetrievalSize = this.messageRetrievalSize;
            build.messageQueryTimeout  = this.messageQueryTimeout;
            build.region               = this.region;
            build.awsStackEndpoint     = this.awsStackEndpoint;
            build.running              = true;
            build.algorithms           = new HashMap<>();

            return build;
        }

    }

    public void consumerSleep(int seconds){
        try                            { TimeUnit.SECONDS.sleep(seconds); }
        catch (InterruptedException e) { e.printStackTrace(); }
    }


    public void run() {
        int counter = 0;

        // Ensure queues exist
        this.createConnectionQueues();

        while (this.running) {
            System.out.println("---> Sleep Iteration: " + counter);
            System.out.println("Current State: " + this.currentState);

            List<Map<String, String>> messagesContents = new ArrayList<>();

            // Check if timers are expired for different states
            this.checkTimers();
            
            // CHECK CONNECTION QUEUE
            List<Message> messages = new ArrayList<>();
            List<Message> connectionMessages = new ArrayList<>();
            connectionMessages = this.getMessages(this.requestQueueUrl, 3, 5);
            connectionMessages = this.handleMessages(this.requestQueueUrl, connectionMessages);
            messages.addAll(connectionMessages);

            // CHECK USER QUEUE
            List<Message> userMessages = new ArrayList<>();
            if (this.userRequestQueueUrl != null) {
                userMessages = this.getMessages(this.userRequestQueueUrl, 3, 5);
                userMessages = this.handleMessages(this.userRequestQueueUrl, userMessages);
                messages.addAll(userMessages);
            }

            for (Message msg: messages) {
                HashMap<String, String> msgContents = this.processMessage(msg, true);
                messagesContents.add(msgContents);
            }

            for (Map<String, String> msgContents: messagesContents) {
                System.out.println(msgContents);

                if(msgContents.containsKey("msgType")){
                    String msgType = msgContents.get("msgType");
                    if (msgType.equals("connectionRequest")) {
                        this.msgTypeConnectionRequest(msgContents);
                    }
                    else if (msgType.equals("connectionAck")) {
                        this.msgTypeConnectionAck(msgContents);
                    }
                    else if (msgType.equals("statusCheck")) {
                        this.msgTypeStatusCheck(msgContents);
                    }
                    else if(msgType.equals("start_ga")){
                        this.msgTypeStartGa(msgContents);
                    }
                    else if(msgType.equals("stop_ga")){
                        this.msgTypeStopGa(msgContents);
                    }
                    else if (msgType.equals("ping")) {
                        this.msgTypePing(msgContents);
                    }
                    else if(msgType.equals("exit")){
                        System.out.println("----> Exiting gracefully");
                        this.running = false;
                    }
                }
                else{
                    System.out.println("-----> INCOMING MESSAGE DIDN'T HAVE ATTRIBUTE: msgType");
                    // this.consumerSleep(10);
                }
            }
            if (!connectionMessages.isEmpty()) {
                this.deleteMessages(connectionMessages, this.requestQueueUrl);
            }
            if (!userMessages.isEmpty()) {
                this.deleteMessages(userMessages, this.userRequestQueueUrl);
            }
            counter++;
        }
    }

    private void createConnectionQueues() {
        String[] requestQueueUrls = this.requestQueueUrl.split("/");
        String requestQueueName = requestQueueUrls[requestQueueUrls.length-1];

        CreateQueueRequest deadQueueRequest = CreateQueueRequest.builder()
            .queueName("dead-letter")
            .build();
        CreateQueueResponse response = sqsClient.createQueue(deadQueueRequest);
        String deadQueueUrl = response.queueUrl();
        ArrayList<QueueAttributeName> attrList = new ArrayList<>();
        attrList.add(QueueAttributeName.QUEUE_ARN);
        GetQueueAttributesRequest attrRequest = GetQueueAttributesRequest.builder()
            .queueUrl(deadQueueUrl)
            .attributeNames(attrList)
            .build();
        GetQueueAttributesResponse attrResponse = sqsClient.getQueueAttributes(attrRequest);
        String deadQueueArn = attrResponse.attributes().get(QueueAttributeName.QUEUE_ARN);

        Map<QueueAttributeName, String> queueAttrs = new HashMap<>();
        queueAttrs.put(QueueAttributeName.MESSAGE_RETENTION_PERIOD, Integer.toString(5*60));
        queueAttrs.put(QueueAttributeName.REDRIVE_POLICY, "{\"maxReceiveCount\":\"3\", \"deadLetterTargetArn\":\"" + deadQueueArn + "\"}");
        CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
            .queueName(requestQueueName)
            .attributes(queueAttrs)
            .build();
        response = sqsClient.createQueue(createQueueRequest);
        String requestQueueUrl = response.queueUrl();
        SetQueueAttributesRequest setAttrReq = SetQueueAttributesRequest.builder()
            .queueUrl(requestQueueUrl)
            .attributes(queueAttrs)
            .build();
        sqsClient.setQueueAttributes(setAttrReq);

        String[] responseQueueUrls = this.responseQueueUrl.split("/");
        String responseQueueName = responseQueueUrls[responseQueueUrls.length-1];
        createQueueRequest = CreateQueueRequest.builder()
            .queueName(responseQueueName)
            .attributes(queueAttrs)
            .build();
        response = sqsClient.createQueue(createQueueRequest);
        String responseQueueUrl = response.queueUrl();
        setAttrReq = SetQueueAttributesRequest.builder()
            .queueUrl(responseQueueUrl)
            .attributes(queueAttrs)
            .build();
        sqsClient.setQueueAttributes(setAttrReq);

        this.deadLetterQueueArn = deadQueueArn;
    }

    private void checkTimers() {
        switch (this.currentState) {
            case WAITING_FOR_USER:
                if (System.currentTimeMillis() - this.lastPingTime > 60*60*1000) {
                    this.downsizeAwsService();
                }
                break;
            case WAITING_FOR_ACK:
                if (System.currentTimeMillis() - this.lastPingTime > 1*60*1000) {
                    this.currentState = State.WAITING_FOR_USER;
                    this.userRequestQueueUrl = null;
                    this.userResponseQueueUrl = null;
                }
                break;
            case READY:
                if (System.currentTimeMillis() - this.lastPingTime > 5*60*1000) {
                    this.currentState = State.WAITING_FOR_USER;
                    this.userRequestQueueUrl = null;
                    this.userResponseQueueUrl = null;
                }
                break;
            default:
                break;
        }
    }

    private List<Message> handleMessages(String queueUrl, List<Message> messages) {
        List<Message> processedMessages = new ArrayList<>();
        for (Message msg: messages) {
            HashMap<String, String> msgContents = this.processMessage(msg, false);
            if (isMessageAllowed(msgContents)) {
                processedMessages.add(msg);
            }
            else {
                // Reject the message and send back to queue
                ChangeMessageVisibilityRequest changeMessageVisibilityRequest = ChangeMessageVisibilityRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(msg.receiptHandle())
                    .visibilityTimeout(1)
                    .build();
                this.sqsClient.changeMessageVisibility(changeMessageVisibilityRequest);
            }
        }
        return processedMessages;
    }

    // Filter valid messages based on the current state of the system
    private boolean isMessageAllowed(Map<String, String> msgContents) {
        String msgType = msgContents.get("msgType");
        List<String> allowedTypes = new ArrayList<>();
        switch (this.currentState) {
            case WAITING_FOR_USER:
                allowedTypes = Arrays.asList("connectionRequest", "statusCheck");
                break;
            case WAITING_FOR_ACK:
                allowedTypes = Arrays.asList("connectionAck", "statusCheck");
                break;
            case READY:
                allowedTypes = Arrays.asList("start_ga", "stop_ga", "ping", "statusCheck", "exit");
                break;
        }
        // Check for both allowedTypes and UUID match
        boolean isAllowed = false;
        if (allowedTypes.contains(msgType)) {
            isAllowed = true;
            if (msgContents.containsKey("UUID")) {
                String msgUUID = msgContents.get("UUID");
                if (!msgUUID.equals(this.uuid)) {
                    isAllowed = false;
                }
            }
        }
        return isAllowed;
    }


    // ---> MESSAGE TYPES
    private void msgTypeConnectionRequest(Map<String, String> msgContents) {
        String userId = msgContents.get("user_id");
        this.userId = Integer.parseInt(userId);

        // Create queues for private communication
        QueueUrls queueUrls = createUserQueues(userId);

        final Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        messageAttributes.put("msgType",
                MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue("isAvailable")
                        .build()
        );
        messageAttributes.put("UUID",
                MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(this.uuid)
                        .build()
        );
        messageAttributes.put("type",
                MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue("genetic-algorithm")
                        .build()
        );
        messageAttributes.put("user_id",
                MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(userId)
                        .build()
        );
        messageAttributes.put("request_queue_url",
                MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(queueUrls.requestUrl)
                        .build()
        );
        messageAttributes.put("response_queue_url",
                MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(queueUrls.responseUrl)
                        .build()
        );

        this.sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(this.responseQueueUrl)
                .messageBody("")
                .messageAttributes(messageAttributes)
                .delaySeconds(0)
                .build());
        this.currentState = State.WAITING_FOR_ACK;
        this.userRequestQueueUrl = queueUrls.requestUrl;
        this.userResponseQueueUrl = queueUrls.responseUrl;
        this.lastPingTime = System.currentTimeMillis();
    }

    private void msgTypeConnectionAck(Map<String, String> msgContents) {
        String receivedUUID = msgContents.get("UUID");
        String vassarUrl = msgContents.get("vassar_url");

        if (receivedUUID.equals(this.uuid)) {
            this.currentState = State.READY;
            this.vassarRequestQueueUrl = vassarUrl;
        }
        else {
            System.out.println("UUID does not match!");
        }
    }

    private void msgTypeStatusCheck(Map<String, String> msgContents) {
        final Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        messageAttributes.put("msgType",
                MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue("currentStatus")
                        .build()
        );
        String currentStatus = "";
        switch (this.currentState) {
            case WAITING_FOR_USER:
                currentStatus = "waiting_for_user";
                break;
            case WAITING_FOR_ACK:
                currentStatus = "waiting_for_ack";
                break;
            case READY:
                currentStatus = "ready";
                break;
        }
        messageAttributes.put("current_status",
                MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(currentStatus)
                        .build()
        );

        this.sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(this.userResponseQueueUrl)
                .messageBody("")
                .messageAttributes(messageAttributes)
                .delaySeconds(0)
                .build());
        this.lastPingTime = System.currentTimeMillis();
    }

    private void msgTypePing(Map<String, String> msgContents) {
        this.lastPingTime = System.currentTimeMillis();
    }

    // ---> ALGORITHMS
    private void msgTypeStartGa(Map<String, String> msgContents){
        deleteAllDoneGAs();

        String maxEvals  = msgContents.get("maxEvals");
        String crossoverProbability  = msgContents.get("crossoverProbability");
        String mutationProbability  = msgContents.get("mutationProbability");
        String group_id  = msgContents.get("group_id");
        String problem_id  = msgContents.get("problem_id");
        String ga_id  = msgContents.get("ga_id");

        System.out.println("\n-------------------- ALGORITHM REQUEST --------------------");
        System.out.println("---------------> MAX EVALS: " + maxEvals);
        System.out.println("---> CROSSOVER PROBABILITY: " + crossoverProbability);
        System.out.println("----> MUTATION PROBABILITY: " + mutationProbability);
        System.out.println("----------------> GROUP ID: " + group_id);
        System.out.println("--------------> PROBLEM ID: " + problem_id);
        System.out.println("--------------> APOLLO URL: " + this.apolloUrl);
        System.out.println("------> AWS STACK ENDPOINT: " + this.awsStackEndpoint);
        System.out.println("--------> VASSAR QUEUE URL: " + this.vassarRequestQueueUrl);
        System.out.println("-------------------> GA ID: " + ga_id);
        System.out.println("----------------------------------------------------------\n");
        //this.consumerSleep(3);

        OkHttpClient http   = new OkHttpClient.Builder().build();
        ApolloClient apollo = ApolloClient.builder().serverUrl(this.apolloUrl).okHttpClient(http).build();

        SqsClient sqsClient = SqsClient.builder()
                .region(this.region)
                .endpointOverride(URI.create(this.awsStackEndpoint))
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .build();

        Algorithm process = new Algorithm.Builder(this.vassarRequestQueueUrl)
                .setGaID(ga_id)
                .setSqsClient(sqsClient)
                .setProblemId(Integer.parseInt(problem_id))
                .setApolloClient(apollo)
                .setMaxEvals(Integer.parseInt(maxEvals))
                .setCrossoverProbability(Double.parseDouble(crossoverProbability))
                .setMutationProbability(Double.parseDouble(mutationProbability))
                .getProblemData(Integer.parseInt(problem_id))
                .build();

        // RUN CONSUMER
        Thread algorithm_thread = new Thread(process);

        this.algorithms.put(String.valueOf(ga_id), new Thread(process));
        this.algorithms.get(String.valueOf(ga_id)).start();
    }

    private int msgTypeStopGa(Map<String, String> msgContents){
        String id  = msgContents.get("ga_id");

        System.out.println("---> STOPPING GA: " + id);

        if (this.algorithms.containsKey(id) && this.algorithms.get(id).isAlive())  {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(System.getenv("RABBITMQ_HOST"));
            String queueName = id + "_brainga";

            try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {
                channel.queueDeclare(queueName, false, false, false, null);
                String message = "close";
                channel.basicPublish("", queueName, null, message.getBytes("UTF-8"));
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            return 0;
        }

        // Remove all dead Threads
        deleteAllDoneGAs();

        return 1;
    }

    private void deleteAllDoneGAs() {
        this.algorithms.entrySet().removeIf(entry -> !entry.getValue().isAlive());
    }


    // ---> MESSAGE FLOW
    // 1.
    private List<Message> getMessages(String queueUrl, int maxMessages, int waitTimeSeconds){
        ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .waitTimeSeconds(waitTimeSeconds)
                .maxNumberOfMessages(maxMessages)
                .attributeNames(QueueAttributeName.ALL)
                .messageAttributeNames("All")
                .build();
        return this.sqsClient.receiveMessage(receiveMessageRequest).messages();
    }

    // 2.
    public HashMap<String, String> processMessage(Message msg, boolean printInfo){
        HashMap<String, String> contents = new HashMap<>();
        contents.put("body", msg.body());
        for(String key: msg.messageAttributes().keySet()){
            contents.put(key, msg.messageAttributes().get(key).stringValue());
        }
        if (printInfo) {
            System.out.println("\n--------------- SQS MESSAGE ---------------");
            System.out.println("--------> BODY: " + msg.body());
            for(String key: msg.messageAttributes().keySet()){
                System.out.println("---> ATTRIBUTE: " + key + " - " + msg.messageAttributes().get(key).stringValue());
            }
            System.out.println("-------------------------------------------\n");
        }
        // this.consumerSleep(5);
        return contents;
    }

    // 3.
    private void deleteMessages(List<Message> messages, String queueUrl) {
        for (Message message : messages) {
            DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build();
            this.sqsClient.deleteMessage(deleteMessageRequest);
        }
    }

    private class QueueUrls {
        public String requestUrl;
        public String responseUrl;
    }

    private QueueUrls createUserQueues(String userId) {
        String requestQueueName = "user-queue-ga-request-" + userId;
        String responseQueueName = "user-queue-ga-response-" + userId;
        Map<QueueAttributeName, String> queueAttrs = new HashMap<>();
        queueAttrs.put(QueueAttributeName.MESSAGE_RETENTION_PERIOD, Integer.toString(5*60));
        queueAttrs.put(QueueAttributeName.REDRIVE_POLICY, "{\"maxReceiveCount\":\"3\", \"deadLetterTargetArn\":\"" + this.deadLetterQueueArn + "\"}");
        CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
            .queueName(requestQueueName)
            .attributes(queueAttrs)
            .build();
        CreateQueueResponse response = sqsClient.createQueue(createQueueRequest);
        String requestQueueUrl = response.queueUrl();

        SetQueueAttributesRequest setAttrReq = SetQueueAttributesRequest.builder()
            .queueUrl(requestQueueUrl)
            .attributes(queueAttrs)
            .build();
        sqsClient.setQueueAttributes(setAttrReq);

        createQueueRequest = CreateQueueRequest.builder()
            .queueName(responseQueueName)
            .attributes(queueAttrs)
            .build();
        response = sqsClient.createQueue(createQueueRequest);
        String responseQueueUrl = response.queueUrl();

        setAttrReq = SetQueueAttributesRequest.builder()
            .queueUrl(responseQueueUrl)
            .attributes(queueAttrs)
            .build();
        sqsClient.setQueueAttributes(setAttrReq);

        QueueUrls returnVal = new QueueUrls();
        returnVal.requestUrl = requestQueueUrl;
        returnVal.responseUrl = responseQueueUrl;

        return returnVal;
    }

    private void downsizeAwsService() {
        // Only do this if in AWS
        if (System.getenv("DEPLOYMENT_TYPE").equals("AWS")) {
            // Check service for number of tasks
            String clusterArn = System.getenv("CLUSTER_ARN");
            String serviceArn = System.getenv("SERVICE_ARN");
            final EcsClient ecsClient = EcsClient.builder()
                                                 .region(Region.US_EAST_2)
                                                 .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                                                 .build();
            DescribeServicesRequest request = DescribeServicesRequest.builder()
                                                                     .cluster(clusterArn)
                                                                     .services(serviceArn)
                                                                     .build();
            DescribeServicesResponse response = ecsClient.describeServices(request);
            if (response.hasServices()) {
                Service service = response.services().get(0);
                Integer desiredCount = service.desiredCount();
                // Downscale tasks if more than 5
                if (desiredCount > 5) {
                    UpdateServiceRequest updateRequest = UpdateServiceRequest.builder()
                                                                             .cluster(clusterArn)
                                                                             .desiredCount(desiredCount-1)
                                                                             .service(serviceArn)
                                                                             .build();
                    UpdateServiceResponse updateResponse = ecsClient.updateService(updateRequest);

                    // Close myself as the extra task
                    String taskArn = getTaskArn();
                    StopTaskRequest stopRequest = StopTaskRequest.builder()
                                                                 .cluster(clusterArn)
                                                                 .task(taskArn)
                                                                 .build();
                    StopTaskResponse stopResponse = ecsClient.stopTask(stopRequest);
                }
            }
        }
    }

    private String getTaskArn() {
        String taskArn = "";
        try {
            String baseUrl = System.getenv("ECS_CONTAINER_METADATA_URI_V4");
            URL url = new URL(baseUrl + "/task");

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.connect();

            //Getting the response code
            int responsecode = conn.getResponseCode();

            if (responsecode != 200) {
                throw new RuntimeException("HttpResponseCode: " + responsecode);
            }
            else {

                String inline = "";
                Scanner scanner = new Scanner(url.openStream());

                //Write all the JSON data into a string using a scanner
                while (scanner.hasNext()) {
                    inline += scanner.nextLine();
                }

                //Close the scanner
                scanner.close();

                //Using the JSON simple library parse the string into a json object
                JSONParser parse = new JSONParser();
                JSONObject responseObj = (JSONObject) parse.parse(inline);

                //Get the required object from the above created object
                taskArn = (String)responseObj.get("TaskARN");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return taskArn;
    }


}
