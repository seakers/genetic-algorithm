package sqs;


import algorithm.CometGA;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import database.DatabaseAPI;


import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;



public class Consumer implements Runnable {
    
    private enum State {
        READY, RUNNING
    }

    private Gson gson = new Gson();
    private boolean   running;
    private SqsClient sqsClient;
    private Ec2Client ec2Client;
    private String privateRequestUrl = System.getenv("PRIVATE_REQUEST_URL");
    private String privateResponseUrl = System.getenv("PRIVATE_RESPONSE_URL");
    private HashMap<String, Thread> gaThreadPool;
    private HashMap<String, ConcurrentLinkedQueue<String>> privateQueuePool;
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
            
            build.sqsClient = this.sqsClient;
            build.running = true;
            build.ec2Client = this.ec2Client;
            build.privateQueuePool = new HashMap<>();

            // --> PING
            build.pingConsumerQueue         = new ConcurrentLinkedQueue<>();
            build.pingConsumerQueueResponse = new ConcurrentLinkedQueue<>();
            build.pingConsumer = new PingConsumer(build.pingConsumerQueue, build.pingConsumerQueueResponse);
            build.pingThread   = null;
            build.gaThreadPool = new HashMap<>();
            
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
                        this.msgTypeStartGa(msgContents);
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
        }
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
        this.currentState = State.READY;
        Map<String, String> status_message = new HashMap<>();
        JsonObject message = new JsonObject();
        message.addProperty("STATUS", "READY");
        status_message.put("controller", this.gson.toJson(message));
        this.pingConsumerQueue.add(status_message);
    }

    private void sendPingStatus(){
        Map<String, String> status_message = new HashMap<>();
        JsonObject message = new JsonObject();
        message.addProperty("STATUS", "RUNNING");
        status_message.put("controller", this.gson.toJson(message));
        this.pingConsumerQueue.add(status_message);
    }

    private void closePingThread(){
        System.out.println("--> CLOSING PING THREAD");
        Map<String, String> status_message = new HashMap<>();
        JsonObject message = new JsonObject();
        message.addProperty("exit", "true");
        status_message.put("controller", "exit");
        this.pingConsumerQueue.add(status_message);

        try{
            this.pingThread.join();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }


//     __  __                                       _    _                    _  _  _
//    |  \/  |                                     | |  | |                  | || |(_)
//    | \  / |  ___  ___  ___   __ _   __ _   ___  | |__| |  __ _  _ __    __| || | _  _ __    __ _
//    | |\/| | / _ \/ __|/ __| / _` | / _` | / _ \ |  __  | / _` || '_ \  / _` || || || '_ \  / _` |
//    | |  | ||  __/\__ \\__ \| (_| || (_| ||  __/ | |  | || (_| || | | || (_| || || || | | || (_| |
//    |_|  |_| \___||___/|___/ \__,_| \__, | \___| |_|  |_| \__,_||_| |_| \__,_||_||_||_| |_| \__, |
//                                     __/ |                                                   __/ |
//                                    |___/                                                   |___/

    private List<Message> checkPrivateQueue(){
        List<Message> brainMessages = new ArrayList<>();
        if(this.privateRequestUrl != null){
            brainMessages = this.getMessages(this.privateRequestUrl, 1, 2);
            brainMessages = this.handleMessages(this.privateRequestUrl, brainMessages);
        }
        return brainMessages;
    }
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
    private void deleteMessages(List<Message> messages, String queueUrl) {
        for (Message message : messages) {
            DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build();
            this.sqsClient.deleteMessage(deleteMessageRequest);
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


    // ---------------
    // --- RECEIVE ---
    // ---------------

    private int msgTypeStopGa(Map<String, String> msgContents) {
        System.out.println("---> STOPPING GA");
        String gaId = msgContents.get("ga_id");
        
        if(this.gaThreadPool.containsKey(gaId)){
            Thread gaThread = this.gaThreadPool.get(gaId);
            if (gaThread != null && gaThread.isAlive()) {
                if(this.privateQueuePool.containsKey(gaId)){
                    System.out.println("--> SENDING STOP GA MESSAGE INTERNAL");
                    this.privateQueuePool.get(gaId).offer("stop");
                }
            }
        }
        else{
            System.out.println("--> ERROR, GA THREAD DOES NOT EXIST IN POOL FOR: " + gaId);
        }
        this.sendExitMessage(gaId);
        return 1;
    }

    private void msgTypeStartGa(Map<String, String> msgContents){

        // --> 1. Get run parameters / pring
        String maxEvals  = msgContents.get("maxEvals");
        String crossoverProbability  = msgContents.get("crossoverProbability");
        String mutationProbability   = msgContents.get("mutationProbability");
        int problemId  = Integer.parseInt(msgContents.get("problem_id"));
        int datasetId  = Integer.parseInt(msgContents.get("dataset_id"));
        int userId = Integer.parseInt(msgContents.get("user_id"));
        int initialPopSize = Integer.parseInt(msgContents.get("initialPopSize"));
        String gaId = msgContents.get("ga_id");
        List<Integer> objective_ids = this.parseObjectiveIds(msgContents.get("objective_ids"));

        System.out.println("\n-------------------- ALGORITHM REQUEST --------------------");
        System.out.println("---------------> MAX EVALS: " + maxEvals);
        System.out.println("-------------------> GA ID: " + gaId);
        System.out.println("----------> OBJECTIVE IDS : " + msgContents.get("objective_ids"));
        System.out.println("---> CROSSOVER PROBABILITY: " + crossoverProbability);
        System.out.println("----> MUTATION PROBABILITY: " + mutationProbability);
        System.out.println("--------------> PROBLEM ID: " + problemId);
        System.out.println("--------------> DATASET ID: " + datasetId);
        System.out.println("--------------> APOLLO URL: " + System.getenv("APOLLO_URL"));
        System.out.println("----------> EVAL QUEUE URL: " + System.getenv("EVAL_REQUEST_URL"));
        System.out.println("----------------------------------------------------------\n");



        // --> 2. Delete current GA with gaId if exists
        this.stopGaThread(gaId);

        // --> 3. Create new ga thread
        ConcurrentLinkedQueue<String> gaQueue = new ConcurrentLinkedQueue<>();

        HashMap<String, String> gaParameters = new HashMap<>();
        gaParameters.put("maxEvals", maxEvals);
        gaParameters.put("popSize", String.valueOf(initialPopSize));
        gaParameters.put("crossoverProbability", crossoverProbability);
        gaParameters.put("mutationProbability", mutationProbability);

        DatabaseAPI databaseAPI = new DatabaseAPI(userId, problemId, datasetId, gaId, objective_ids);

        CometGA process = new CometGA(databaseAPI, gaParameters, gaQueue, this.pingConsumerQueue);

        Thread gaThread = new Thread(process);
        gaThread.start();

        this.privateQueuePool.put(gaId, gaQueue);
        this.gaThreadPool.put(gaId, gaThread);

        this.currentState = State.RUNNING;

        this.sendPingStatus();
    }



    // ------------
    // --- SEND ---
    // ------------

    public void sendExitMessage(String gaId){
        final Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        messageAttributes.put("msgType",
                MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue("exit")
                        .build()
        );
        messageAttributes.put("gaId",
                MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(gaId)
                        .build()
        );
        this.sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(this.privateResponseUrl)
                .messageBody("ga_message")
                .messageAttributes(messageAttributes)
                .delaySeconds(0)
                .build());
    }

    public void sendRunningMessage(String gaId){
        final Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        messageAttributes.put("msgType",
                MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue("running")
                        .build()
        );
        messageAttributes.put("gaId",
                MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(gaId)
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


    // --------------------
    // --- EC2 SERVICES ---
    // --------------------

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


    // ---------------
    // --- HELPERS ---
    // ---------------

    private List<Integer> parseObjectiveIds(String array){
        ArrayList<Integer> result = new ArrayList<>();
        String substr = array.substring(1, array.length()-1);
        List<String> items = Arrays.asList(substr.split("\\s*,\\s*"));
        for(String item: items){
            result.add(Integer.parseInt(item));
        }
        return result;
    }

    private void stopGaThread(String gaId){
        if(this.privateQueuePool.containsKey(gaId)){
            // --> Stop current thread(gaId) if exists
            ConcurrentLinkedQueue<String> thread_queue = this.privateQueuePool.get(gaId);
            if(this.gaThreadPool.containsKey(gaId) && this.gaThreadPool.get(gaId) != null && this.gaThreadPool.get(gaId).isAlive()){
                Thread curr_thread = this.gaThreadPool.get(gaId);
                try {
                    thread_queue.add("{ \"type\": \"stop\" }");
                    curr_thread.join();
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            this.gaThreadPool.remove(gaId);
            this.privateQueuePool.remove(gaId);
        }
    }



}

