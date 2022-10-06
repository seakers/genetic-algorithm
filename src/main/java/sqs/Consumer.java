package sqs;

import algorithm.GAThread;
import algorithm.GAThread.RunType;

import com.apollographql.apollo.ApolloClient;
import okhttp3.OkHttpClient;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest;
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.StopTaskRequest;
import software.amazon.awssdk.services.ecs.model.StopTaskResponse;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public class Consumer implements Runnable {
    
    private enum State {
        READY, RUNNING
    }

    private boolean   debug;
    private boolean   running;
    private SqsClient sqsClient;
    private Ec2Client ec2Client;

    private String vassarRequestQueueUrl = System.getenv("EVAL_REQUEST_URL");
    private String privateRequestUrl = System.getenv("PRIVATE_REQUEST_URL");
    private String privateResponseUrl = System.getenv("PRIVATE_RESPONSE_URL");
    private String    apolloUrl = System.getenv("APOLLO_URL");
    private int       messageRetrievalSize = Integer.parseInt(System.getenv("MESSAGE_RETRIEVAL_SIZE"));
    private int       messageQueryTimeout = Integer.parseInt(System.getenv("MESSAGE_QUERY_TIMEOUT"));
    private Thread    gaThread;
    private ConcurrentLinkedQueue<String> privateQueue;
    private State     currentState = State.READY;


    // --> PING
    private ConcurrentLinkedQueue<Map<String, String>> pingConsumerQueue;
    private ConcurrentLinkedQueue<Map<String, String>> pingConsumerQueueResponse;
    private PingConsumer                               pingConsumer;
    private Thread                                     pingThread = null;







    
    public static class Builder{
        
        private SqsClient sqsClient;
        private Ec2Client ec2Client;
        private boolean   debug;

        public Builder(SqsClient sqsClient){
            this.sqsClient = sqsClient;
        }

        public Builder setEC2Client(Ec2Client ec2Client) {
            this.ec2Client = ec2Client;
            return this;
        }

        public Builder debug(boolean debug){
            this.debug = debug;
            return this;
        }

        public Consumer build(){
            Consumer build = new Consumer();
            
            build.sqsClient      = this.sqsClient;
            build.debug            = this.debug;
            build.running              = true;
            build.ec2Client = this.ec2Client;
            build.privateQueue = new ConcurrentLinkedQueue<String>();

            // --> PING
            build.pingConsumerQueue         = new ConcurrentLinkedQueue<>();
            build.pingConsumerQueueResponse = new ConcurrentLinkedQueue<>();
            build.pingConsumer = new PingConsumer(build.pingConsumerQueue, build.pingConsumerQueueResponse);
            build.pingThread   = null;
            
            return build;
        }
    }
    


//     _____
//    |  __ \
//    | |__) |   _ _ __
//    |  _  / | | | '_ \
//    | | \ \ |_| | | | |
//    |_|  \_\__,_|_| |_|

    public void run() {
        int counter = 0;

        this.startPingThread();

        while (this.running) {
            System.out.println("-----> (Cons) Loop iteration: " + counter + " -- " + this.currentState);

            // --> 1. Check ping queue
            this.checkPingQueue();
            if(!this.running){break;}


            // --> 2. Handle private messages
            List<Message> privateMessages = this.checkPrivateQueue();
            List<Map<String, String>> privateMessageContents = new ArrayList<>();
            for (Message msg: privateMessages) {
                HashMap<String, String> msgContents = this.processMessage(msg, true);
                privateMessageContents.add(msgContents);
            }
            for (Map<String, String> msgContents: privateMessageContents) {
                if (msgContents.containsKey("msgType")) {
                    String msgType = msgContents.get("msgType");
                    if(msgType.equals("start_ga")){
                        this.msgTypeStartGa(msgContents, "interactive");
                    }
                    else if(msgType.equals("start_bulk_ga")){
                        this.msgTypeStartGa(msgContents, "bulk");
                    }
                    else if(msgType.equals("apply_feature")){
                        this.msgTypeApplyFeature(msgContents);
                    }
                    else if(msgType.equals("stop_ga")){
                        this.msgTypeStopGa(msgContents);
                    }
                    else if(msgType.equals("exit")){
                        System.out.println("----> Exiting gracefully");
                        this.running = false;
                    }
                }
            }
            if (!privateMessages.isEmpty()) {
                this.deleteMessages(privateMessages, this.privateRequestUrl);
            }

            counter++;
//            List<Map<String, String>> messagesContents = new ArrayList<>();
            
            // Check if timers are expired for different states
//            this.checkTimers();
            
            // CHECK CONNECTION QUEUE
//            List<Message> messages = new ArrayList<>();
//            List<Message> connectionMessages = new ArrayList<>();
//            List<Message> userMessages = new ArrayList<>();
//            if (this.currentState == State.WAITING_FOR_USER) {
//                connectionMessages = this.getMessages(this.requestQueueUrl, 1, 1);
//                connectionMessages = this.handleMessages(this.requestQueueUrl, connectionMessages);
//                messages.addAll(connectionMessages);
//            }
            
            // CHECK USER QUEUE
//            if (this.currentState == State.WAITING_FOR_ACK || this.currentState == State.READY) {
//                if (this.userRequestQueueUrl != null) {
//                    userMessages = this.getMessages(this.userRequestQueueUrl, 5, 1);
//                    userMessages = this.handleMessages(this.userRequestQueueUrl, userMessages);
//                    messages.addAll(userMessages);
//                }
//            }

            // COMBINE QUEUE MESSAGES
//            for (Message msg: messages) {
//                HashMap<String, String> msgContents = this.processMessage(msg, true);
//                messagesContents.add(msgContents);
//            }
            
//            for (Map<String, String> msgContents: messagesContents) {
//                System.out.println(msgContents);
//
//                // "start_ga", "start_bulk_ga", "apply_feature", "stop_ga", "ping", "statusCheck", "reset", "exit"
//
//                if(msgContents.containsKey("msgType")){
//                    String msgType = msgContents.get("msgType");
////                    if (msgType.equals("connectionRequest")) {
////                        this.msgTypeConnectionRequest(msgContents);
////                    }
////                    else if (msgType.equals("connectionAck")) {
////                        this.msgTypeConnectionAck(msgContents);
////                    }
////                    else if (msgType.equals("statusCheck")) {
////                        this.msgTypeStatusCheck(msgContents);
////                    }
//                    if(msgType.equals("start_ga")){
//                        this.msgTypeStartGa(msgContents, "interactive");
//                    }
//                    else if(msgType.equals("start_bulk_ga")){
//                        this.msgTypeStartGa(msgContents, "bulk");
//                    }
//                    else if(msgType.equals("apply_feature")){
//                        this.msgTypeApplyFeature(msgContents);
//                    }
//                    else if(msgType.equals("stop_ga")){
//                        this.msgTypeStopGa(msgContents);
//                    }
////                    else if (msgType.equals("ping")) {
////                        this.msgTypePing(msgContents);
////                    }
////                    else if (msgType.equals("reset")) {
////                        this.msgTypeReset(msgContents);
////                    }
//                    else if(msgType.equals("exit")){
//                        System.out.println("----> Exiting gracefully");
//                        this.running = false;
//                    }
//                }
//                else{
//                    System.out.println("-----> INCOMING MESSAGE DIDN'T HAVE ATTRIBUTE: msgType");
//                    // this.consumerSleep(10);
//                }
//            }
//            if (!connectionMessages.isEmpty()) {
//                this.deleteMessages(connectionMessages, this.requestQueueUrl);
//            }
//            if (!userMessages.isEmpty()) {
//                this.deleteMessages(userMessages, this.userRequestQueueUrl);
//            }


//            if (this.pendingReset) {
//                this.currentState = State.READY;
//                this.userRequestQueueUrl = null;
//                this.userResponseQueueUrl = null;
//                this.pendingReset = false;
//            }
//            counter++;
        }
        this.sendExitMessage();
        this.closePingThread();
        if(System.getenv("DEPLOYMENT_TYPE").equals("AWS")){
            this.stopInstance();
        }
    }



//     _____ _               _    _                 _ _ _
//    |  __ (_)             | |  | |               | | (_)
//    | |__) | _ __   __ _  | |__| | __ _ _ __   __| | |_ _ __   __ _
//    |  ___/ | '_ \ / _` | |  __  |/ _` | '_ \ / _` | | | '_ \ / _` |
//    | |   | | | | | (_| | | |  | | (_| | | | | (_| | | | | | | (_| |
//    |_|   |_|_| |_|\__, | |_|  |_|\__,_|_| |_|\__,_|_|_|_| |_|\__, |
//                    __/ |                                      __/ |
//                   |___/                                      |___/

    private void startPingThread(){
        System.out.println("--> RUNNING PING THREAD");
        this.pingThread = new Thread(this.pingConsumer);
        this.pingThread.start();
        this.sendReadyStatus();
    }

    private void checkPingQueue() {
        if(!this.pingConsumerQueueResponse.isEmpty()){
            Map<String, String> msgContents = this.pingConsumerQueueResponse.poll();
            System.out.println("--> THERE IS A STOP MESSAGE FROM THE PING CONTAINER ");
            System.out.println(msgContents);
            this.running = false;
        }
    }

    private void sendReadyStatus(){
        Map<String, String> status_message = new HashMap<>();
        status_message.put("STATUS", "READY");
        status_message.put("PROBLEM_ID", "-----");
        status_message.put("GROUP_ID", "-----");
        status_message.put("DATASET_ID", "-----");
        this.pingConsumerQueue.add(status_message);
    }

    private void sendRunningStatus(int groupId, int problemId){
        Map<String, String> status_message = new HashMap<>();
        status_message.put("STATUS", "RUNNING");
        status_message.put("PROBLEM_ID", String.valueOf(problemId));
        status_message.put("GROUP_ID", String.valueOf(groupId));
        status_message.put("DATASET_ID", "-----");
        this.pingConsumerQueue.add(status_message);
    }

    private void closePingThread(){
        System.out.println("--> CLOSING PING THREAD");
        Map<String, String> status_message = new HashMap<>();
        status_message.put("msgType", "stop");
        this.pingConsumerQueue.add(status_message);

        try{
            this.pingThread.join();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }




//     __  __                                  _______
//    |  \/  |                                |__   __|
//    | \  / | ___  ___ ___  __ _  __ _  ___     | |_   _ _ __   ___  ___
//    | |\/| |/ _ \/ __/ __|/ _` |/ _` |/ _ \    | | | | | '_ \ / _ \/ __|
//    | |  | |  __/\__ \__ \ (_| | (_| |  __/    | | |_| | |_) |  __/\__ \
//    |_|  |_|\___||___/___/\__,_|\__, |\___|    |_|\__, | .__/ \___||___/
//                                 __/ |             __/ | |
//                                |___/             |___/|_|

    // --> CHECK MESSAGES
    private List<Message> checkPrivateQueue(){
        List<Message> brainMessages = new ArrayList<>();
        if(this.privateRequestUrl != null){
            brainMessages = this.getMessages(this.privateRequestUrl, 1, 2);
            brainMessages = this.handleMessages(this.privateRequestUrl, brainMessages);
        }
        return brainMessages;
    }

    // --> RECEIVE
    private boolean isMessageAllowed(Map<String, String> msgContents) {
        String msgType = msgContents.get("msgType");
        List<String> allowedTypes = new ArrayList<>();
        switch (this.currentState) {
            case READY:
                allowedTypes = Arrays.asList("start_ga", "start_bulk_ga", "apply_feature", "stop_ga", "ping", "statusCheck", "reset", "exit");
                break;
            case RUNNING:
                allowedTypes = Arrays.asList("start_ga", "start_bulk_ga", "apply_feature", "stop_ga", "ping", "statusCheck", "reset", "exit");
                break;
        }
        return allowedTypes.contains(msgType);
    }

    private void msgTypeStartGa(Map<String, String> msgContents, String runType) {
        String maxEvals  = msgContents.get("maxEvals");
        String crossoverProbability  = msgContents.get("crossoverProbability");
        String mutationProbability   = msgContents.get("mutationProbability");
        String algorithmUrl          = msgContents.get("algorithmUrl");
        int groupId  = Integer.parseInt(msgContents.get("group_id"));
        int problemId  = Integer.parseInt(msgContents.get("problem_id"));
        int datasetId  = Integer.parseInt(msgContents.get("dataset_id"));
        String testedFeature = msgContents.getOrDefault("tested_feature", "");
        int maxSeconds = Integer.parseInt(msgContents.getOrDefault("max_seconds", "0"));
        
        System.out.println("\n-------------------- ALGORITHM REQUEST --------------------");
        System.out.println("---------------> MAX EVALS: " + maxEvals);
        System.out.println("---> CROSSOVER PROBABILITY: " + crossoverProbability);
        System.out.println("----> MUTATION PROBABILITY: " + mutationProbability);
        System.out.println("----------------> RUN TYPE: " + runType);
        System.out.println("----------------> GROUP ID: " + groupId);
        System.out.println("--------------> PROBLEM ID: " + problemId);
        System.out.println("--------------> DATASET ID: " + datasetId);
        System.out.println("--------------> APOLLO URL: " + this.apolloUrl);
        System.out.println("--------> VASSAR QUEUE URL: " + this.vassarRequestQueueUrl);
        System.out.println("----------------------------------------------------------\n");
        //this.consumerSleep(3);

        this.sendRunningStatus(groupId, problemId);
        
        OkHttpClient http   = new OkHttpClient.Builder().connectTimeout(600, TimeUnit.SECONDS).readTimeout(600, TimeUnit.SECONDS).writeTimeout(600, TimeUnit.SECONDS).callTimeout(600, TimeUnit.SECONDS).build();
        ApolloClient apollo = ApolloClient.builder().serverUrl(this.apolloUrl).okHttpClient(http).build();
        
        SqsClientBuilder sqsClientBuilder = SqsClient.builder().region(Region.US_EAST_2);
        final SqsClient sqsClient = sqsClientBuilder.build();

        RunType gaRunType = null;
        if (runType.equals("interactive")) {
            gaRunType = RunType.INTERACTIVE;
        }
        else if (runType.equals("bulk")) {
            gaRunType = RunType.BULK;
        }
        
        GAThread process = new GAThread.Builder(algorithmUrl, this.vassarRequestQueueUrl)
                .setSqsClient(sqsClient)
                .setGroupId(groupId)
                .setProblemId(problemId)
                .setDatasetId(datasetId)
                .setApolloClient(apollo)
                .setMaxEvals(Integer.parseInt(maxEvals))
                .setMaxSeconds(maxSeconds)
                .setCrossoverProbability(Double.parseDouble(crossoverProbability))
                .setMutationProbability(Double.parseDouble(mutationProbability))
                .setRunType(gaRunType)
                .setTestedFeature(testedFeature)
                .setPrivateQueue(this.privateQueue)
                .getProblemData(problemId, datasetId)
                .build();

        // RUN CONSUMER
        if (this.gaThread != null && this.gaThread.isAlive()) {
            try {
                this.privateQueue.add("{ \"type\": \"stop\" }");
                this.gaThread.join();
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        this.privateQueue.clear();
        this.gaThread = new Thread(process);
        this.gaThread.start();
        this.currentState = State.RUNNING;
    }

    private void msgTypeApplyFeature(Map<String, String> msgContents) {
        System.out.println("---> APPLYING FEATURE");
        
        if (this.gaThread != null && this.gaThread.isAlive()) {
            this.privateQueue.add("{ \"type\": \"exploreFeature\", \"feature\": \"" + msgContents.get("tested_feature") + "\" }");
        }
    }

    private int msgTypeStopGa(Map<String, String> msgContents) {
        System.out.println("---> STOPPING GA");
        
        if (this.gaThread != null && this.gaThread.isAlive()) {
            this.privateQueue.add("{ \"type\": \"stop\" }");
            return 0;
        }

        this.sendReadyStatus();
        
        return 1;
    }


    // --> SEND
    public void sendExitMessage(){
        final Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        messageAttributes.put("msgType",
                MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue("exit")
                        .build()
        );
        messageAttributes.put("status",
                MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue("finished")
                        .build()
        );
        this.sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(this.privateResponseUrl)
                .messageBody("ga_message")
                .messageAttributes(messageAttributes)
                .delaySeconds(0)
                .build());
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

        List<Message> messages = new ArrayList<>(this.sqsClient.receiveMessage(receiveMessageRequest).messages());

        Collections.sort(messages, (Message a, Message b) -> {
            Long timestamp_a = Long.parseLong(a.attributes().get(MessageSystemAttributeName.SENT_TIMESTAMP));
            Long timestamp_b = Long.parseLong(b.attributes().get(MessageSystemAttributeName.SENT_TIMESTAMP));
            Long diff = timestamp_a - timestamp_b;
            if (diff > 0) {
                return 1;
            }
            else if (diff < 0) {
                return -1;
            }
            else {
                return 0;
            }
        });
        return messages;
    }

    // 2.
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

    // 3.
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

    // 4.
    private void deleteMessages(List<Message> messages, String queueUrl) {
        for (Message message : messages) {
            DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
            .queueUrl(queueUrl)
            .receiptHandle(message.receiptHandle())
            .build();
            this.sqsClient.deleteMessage(deleteMessageRequest);
        }
    }



    // --> AWS
    private void stopInstance(){

        // --> 1. Get instance id
        String identifier = System.getenv("IDENTIFIER");

        Filter filter = Filter.builder()
                .name("tag:IDENTIFIER")
                .values(identifier)
                .build();

        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                .filters(filter)
                .build();

        DescribeInstancesResponse response = this.ec2Client.describeInstances(request);
        if (response.hasReservations() && response.reservations().size() == 1){
            Instance instance = response.reservations().get(0).instances().get(0);
            String instance_id = instance.instanceId();

            // --> 2. Stop instance
            StopInstancesRequest request2 = StopInstancesRequest.builder()
                    .instanceIds(instance_id)
                    .build();
            StopInstancesResponse response2 = this.ec2Client.stopInstances(request2);
        }
        else{
            System.out.println("--> ERROR STOPPING INSTANCE, COULD NOT GET ID");
        }
    }
}
