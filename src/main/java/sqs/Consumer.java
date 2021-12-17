package sqs;

import algorithm.Algorithm;
import com.algorithm.ArchitectureQuery;
import com.algorithm.InstrumentQuery;
import com.algorithm.OrbitCountQuery;
import com.algorithm.ProblemOrbitJoinQuery;
import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.rx2.Rx2Apollo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.reactivex.Observable;
import okhttp3.OkHttpClient;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
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
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;
import software.amazon.awssdk.regions.Region;

import java.io.FileWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public class Consumer implements Runnable {
    
    private enum State {
        READY, RUNNING
    }

    // --> PING
    private String    privateRequestQueue;
    private String    privateResponseQueue;
    private ConcurrentLinkedQueue<Map<String, String>> pingConsumerQueue;
    private ConcurrentLinkedQueue<Map<String, String>> pingConsumerQueueResponse;
    private PingConsumer pingConsumer;
    private Thread pingThread = null;



    private SqsClient sqsClient;
    private EcsClient ecsClient;
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
    private Thread    algorithm;
    private Region    region;
    private String    awsStackEndpoint;
    private State     currentState = State.READY;
    private String    uuid = UUID.randomUUID().toString();
    private long      lastPingTime = System.currentTimeMillis();
    private long      lastDownsizeRequestTime = System.currentTimeMillis();
    private int       userId;
    private int       datasetId;
    private int       problemId;

    private ConcurrentLinkedQueue<String> privateQueue;
    
    public static class Builder{
        
        private SqsClient sqsClient;
        private EcsClient ecsClient;
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
        
        public Builder setECSClient(EcsClient ecsClient) {
            this.ecsClient = ecsClient;
            return this;
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
            build.ecsClient      = this.ecsClient;
            build.apolloUrl      = this.apolloUrl;
            build.requestQueueUrl  = this.requestQueueUrl;
            build.responseQueueUrl = this.responseQueueUrl;
            build.debug            = this.debug;
            build.messageRetrievalSize = this.messageRetrievalSize;
            build.messageQueryTimeout  = this.messageQueryTimeout;
            build.region               = this.region;
            build.awsStackEndpoint     = this.awsStackEndpoint;
            build.running              = true;
            build.privateQueue = new ConcurrentLinkedQueue<String>();
            build.datasetId    = 0;
            build.problemId    = 0;
            build.privateRequestQueue = System.getenv("GA_REQUEST_URL");
            build.privateResponseQueue = System.getenv("GA_RESPONSE_URL");
            build.pingConsumerQueue = new ConcurrentLinkedQueue<>();
            build.pingConsumerQueueResponse = new ConcurrentLinkedQueue<>();
            build.pingConsumer = new PingConsumer(build.pingConsumerQueue, build.pingConsumerQueueResponse);
            
            return build;
        }
        
    }
    
    public void consumerSleep(int seconds){
        try                            { TimeUnit.SECONDS.sleep(seconds); }
        catch (InterruptedException e) { e.printStackTrace(); }
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


            // --> 2. Check private queue
            List<Message> messages = this.checkPrivateQueue();


            // --> 3. Process messages
            List<Map<String, String>> messagesContents = new ArrayList<>();
            for (Message msg: messages) {
                HashMap<String, String> msgContents = this.processMessage(msg, true);
                messagesContents.add(msgContents);
            }

            // --> 4. Handle all messages
            for (Map<String, String> msgContents: messagesContents) {
                System.out.println(msgContents);
                
                if(msgContents.containsKey("msgType")){
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
                        this.sendCloseThread();
                    }
                }
                else{
                    System.out.println("-----> INCOMING MESSAGE DIDN'T HAVE ATTRIBUTE: msgType");
                }
            }

            if (!messages.isEmpty()) {
                this.deleteMessages(messages, this.privateRequestQueue);
            }
            counter++;
        }

        this.closePingThread();
    }


//    _____ _
//    |  __ (_)
//    | |__) | _ __   __ _
//    |  ___/ | '_ \ / _` |
//    | |   | | | | | (_| |
//    |_|   |_|_| |_|\__, |
//                    __/ |
//                   |___/


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

    private void sendRunningStatus(int gorupId, int problemId, int datasetId){
        Map<String, String> status_message = new HashMap<>();
        status_message.put("STATUS", "RUNNING");
        status_message.put("PROBLEM_ID", String.valueOf(problemId));
        status_message.put("GROUP_ID", String.valueOf(gorupId));
        status_message.put("DATASET_ID", String.valueOf(datasetId));
        this.pingConsumerQueue.add(status_message);
    }

    private void sendReadyStatus(){
        Map<String, String> status_message = new HashMap<>();
        status_message.put("STATUS", "READY");
        status_message.put("PROBLEM_ID", "-----");
        status_message.put("GROUP_ID", "-----");
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



//     __  __                                  _    _                 _ _ _
//    |  \/  |                                | |  | |               | | (_)
//    | \  / | ___  ___ ___  __ _  __ _  ___  | |__| | __ _ _ __   __| | |_ _ __   __ _
//    | |\/| |/ _ \/ __/ __|/ _` |/ _` |/ _ \ |  __  |/ _` | '_ \ / _` | | | '_ \ / _` |
//    | |  | |  __/\__ \__ \ (_| | (_| |  __/ | |  | | (_| | | | | (_| | | | | | | (_| |
//    |_|  |_|\___||___/___/\__,_|\__, |\___| |_|  |_|\__,_|_| |_|\__,_|_|_|_| |_|\__, |
//                                 __/ |                                           __/ |
//                                |___/                                           |___/

    private List<Message> checkPrivateQueue(){
        List<Message> messages = new ArrayList<>();
        if(this.privateRequestQueue != null){
            messages = this.getMessages(this.privateRequestQueue, 1, 1);
            messages = this.handleMessages(this.privateRequestQueue, messages);
        }
        return messages;
    }



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
    private List<Message> handleMessages(String queueUrl, List<Message> messages) {
        List<Message> processedMessages = new ArrayList<>();
        int rejectedCount = 0;
        for (Message msg: messages) {
            HashMap<String, String> msgContents = this.processMessage(msg, false);
            if (isMessageAllowed(msgContents)) {
                processedMessages.add(msg);
            }
            else {
                // Delete the message
                this.deleteMessage(msg, this.privateRequestQueue);
                rejectedCount += 1;
            }
        }
        return processedMessages;
    }

    // 2.5
    private boolean isMessageAllowed(Map<String, String> msgContents) {
        String msgType = msgContents.get("msgType");
        List<String> allowedTypes = new ArrayList<>();
        switch (this.currentState) {
            case READY:
                allowedTypes = Arrays.asList("start_ga", "exit");
                break;
            case RUNNING:
                allowedTypes = Arrays.asList("stop_ga", "exit");
                break;
        }
        // Check for both allowedTypes and UUID match
        return allowedTypes.contains(msgType);
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

    // 4.5
    private void deleteMessage(Message message, String queueUrl) {
        DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build();
        this.sqsClient.deleteMessage(deleteMessageRequest);
    }





    // ---> MESSAGE TYPES
    private void msgTypeStartGa(Map<String, String> msgContents) {
        String maxEvals  = msgContents.get("maxEvals");
        String vassar_request_queue = msgContents.get("VASSAR_REQUEST_QUEUE");
        String vassar_response_queue = msgContents.get("VASSAR_RESPONSE_QUEUE");
        int groupId  = Integer.parseInt(msgContents.get("group_id"));
        int problemId  = Integer.parseInt(msgContents.get("problem_id"));
        int datasetId  = Integer.parseInt(msgContents.get("dataset_id"));
        this.datasetId = datasetId;
        this.problemId = problemId;
        String objective_str = msgContents.get("objectives");
        List<String> objective_list = Arrays.asList(objective_str.split(","));

        // --> Set internal status
        this.sendRunningStatus(groupId, problemId, datasetId);


        String testedFeature = msgContents.getOrDefault("tested_feature", "");

        System.out.println("\n-------------------- ALGORITHM REQUEST --------------------");
        System.out.println("---------------> MAX EVALS: " + maxEvals);
        System.out.println("----------------> GROUP ID: " + groupId);
        System.out.println("--------------> PROBLEM ID: " + problemId);
        System.out.println("--------------> DATASET ID: " + datasetId);
        System.out.println("--------------> OBJECTIVES: " + objective_list);
        System.out.println("--------------> APOLLO URL: " + this.apolloUrl);
        System.out.println("------> AWS STACK ENDPOINT: " + this.awsStackEndpoint);
        System.out.println("--------> VASSAR QUEUE URL: " + this.vassarRequestQueueUrl);
        System.out.println("----------------------------------------------------------\n");
        //this.consumerSleep(3);

        OkHttpClient http   = new OkHttpClient.Builder().build();
        ApolloClient apollo = ApolloClient.builder().serverUrl(this.apolloUrl).okHttpClient(http).build();

        SqsClientBuilder sqsClientBuilder = SqsClient.builder()
                .region(this.region);
        if (awsStackEndpoint != null) {
            sqsClientBuilder.endpointOverride(URI.create(this.awsStackEndpoint));
        }
        final SqsClient sqsClient = sqsClientBuilder.build();

        Algorithm process = new Algorithm.Builder(this.privateResponseQueue, vassar_request_queue)
                .setSqsClient(sqsClient)
                .setGroupId(groupId)
                .setProblemId(problemId)
                .setDatasetId(datasetId)
                .setApolloClient(apollo)
                .setMaxEvals(Integer.parseInt(maxEvals))
                .setCrossoverProbability(1.0)
                .setMutationProbability(0.01666)
                .setTestedFeature(testedFeature)
                .setObjectiveList(objective_list)
                .setPrivateQueue(this.privateQueue)
                .getProblemData(problemId, datasetId)
                .build();

        // RUN CONSUMER
        this.algorithm = new Thread(process);
        this.algorithm.start();

        this.currentState = State.RUNNING;
    }

    private int msgTypeStopGa(Map<String, String> msgContents) {
        System.out.println("---> STOPPING GA");

        if (this.algorithm != null && this.algorithm.isAlive()) {
            this.privateQueue.add("stop");
        }

        this.currentState = State.READY;
        this.sendReadyStatus();
        return 1;
    }






    private void sendCloseThread(){
        Map<String, String> status_message = new HashMap<>();
        status_message.put("msgType", "stop");
        this.pingConsumerQueue.add(status_message);
    }



    // --> HELPERS
    private ApolloClient newApolloClient(){
        OkHttpClient http   = new OkHttpClient.Builder().build();
        return ApolloClient.builder().serverUrl(this.apolloUrl).okHttpClient(http).build();
    }

    private void saveDataset(){
        ApolloClient client = this.newApolloClient();
        JsonObject all_data = new JsonObject();

        // --> 1. Get list of instruments
        InstrumentQuery instrumentQuery = InstrumentQuery.builder().problem_id(this.problemId).build();
        ApolloCall<InstrumentQuery.Data> apolloCall  = client.query(instrumentQuery);
        Observable<Response<InstrumentQuery.Data>> observable  = Rx2Apollo.from(apolloCall);
        List<InstrumentQuery.Item> instruments = observable.blockingFirst().getData().items();
        JsonArray instrument_list = new JsonArray();
        for(InstrumentQuery.Item item: instruments){
            instrument_list.add(item.name());
        }
        all_data.add("instruments", instrument_list);

        // --> 2. Get list of orbits
        ProblemOrbitJoinQuery orbitQuery = ProblemOrbitJoinQuery.builder().problem_id(this.problemId).build();
        ApolloCall<ProblemOrbitJoinQuery.Data> apolloCall2  = client.query(orbitQuery);
        Observable<Response<ProblemOrbitJoinQuery.Data>> observable2  = Rx2Apollo.from(apolloCall2);
        List<ProblemOrbitJoinQuery.Item> orbits = observable2.blockingFirst().getData().items();
        JsonArray orbit_list = new JsonArray();
        for(ProblemOrbitJoinQuery.Item item: orbits){
            orbit_list.add(item.Orbit().name());
        }
        all_data.add("orbits", orbit_list);

        // --> 3. Get all architectures
        ArchitectureQuery architectureQuery = ArchitectureQuery.builder()
                .problem_id(this.problemId)
                .dataset_id(this.datasetId)
                .build();
        ApolloCall<ArchitectureQuery.Data> apolloCall3  = client.query(architectureQuery);
        Observable<Response<ArchitectureQuery.Data>> observable3  = Rx2Apollo.from(apolloCall3);
        List<ArchitectureQuery.Item> architectures = observable3.blockingFirst().getData().items();
        JsonArray all_archs = new JsonArray();
        for(ArchitectureQuery.Item item: architectures){
            JsonObject arch = new JsonObject();
            arch.addProperty("input", item.input());
            arch.addProperty("cost", Double.parseDouble(item.cost().toString()));
            arch.addProperty("data_continuity", Double.parseDouble(item.data_continuity().toString()));
            arch.addProperty("programmatic_risk", Double.parseDouble(item.programmatic_risk().toString()));
            arch.addProperty("fairness", Double.parseDouble(item.fairness().toString()));
            for(ArchitectureQuery.ArchitectureScoreExplanation explanation: item.ArchitectureScoreExplanations()){
                String panel_name = explanation.Stakeholder_Needs_Panel().name();
                arch.addProperty(panel_name, Double.parseDouble(explanation.satisfaction().toString()));
            }
            all_archs.add(arch);
        }
        all_data.add("designs", all_archs);

        // --> 4. Save all data to file
        SimpleDateFormat dateFormat = new SimpleDateFormat( "yyyy-MM-dd-HH-mm-ss" );
        String stamp = dateFormat.format( new Date() );
        String file_path = "/app/results/";
        String file_name = "ClimateCentric2__" + stamp + "__.json";
        String full_file = file_path + file_name;
        try{
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            FileWriter outputfile = new FileWriter(full_file);
            gson.toJson(all_data, outputfile);
            outputfile.flush();
            outputfile.close();
        }
        catch (Exception e){
            e.printStackTrace();
            System.out.println("WRITING EXCEPTION");
        }
    }
}
