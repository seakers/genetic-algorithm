package sqs;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PingConsumer implements Runnable {

    private String awsStackEndpoint;
    private SqsClient sqs_client;
    private long lastPingTime;
    public Gson gson = new Gson();


    // --> AWS Evaluator Ping Queues: Brain <--> Evaluator
    private String pingRequestQueue = System.getenv("PING_REQUEST_URL");
    private String pingResponseQueue = System.getenv("PING_RESPONSE_URL");

    // --> Internal Queues: Consumer <--> PingConsumer
    private ConcurrentLinkedQueue<Map<String, String>> mainConsumerQueue;
    private ConcurrentLinkedQueue<Map<String, String>> mainConsumerQueueResponse;

    private Map<String, String> statusStore;


    public PingConsumer(ConcurrentLinkedQueue<Map<String, String>> mainConsumerQueue, ConcurrentLinkedQueue<Map<String, String>> mainConsumerQueueResponse) {
        this.mainConsumerQueue = mainConsumerQueue;
        this.mainConsumerQueueResponse = mainConsumerQueueResponse;
        this.awsStackEndpoint = System.getenv("AWS_STACK_ENDPOINT");
        this.sqs_client = this.newClient();
        this.lastPingTime = System.currentTimeMillis();
        this.statusStore = new HashMap<>();
    }

    public SqsClient newClient() {
        SqsClientBuilder sqsClientBuilder = SqsClient.builder()
                .region(Region.US_EAST_2);
        if (this.awsStackEndpoint != null) {
            sqsClientBuilder.endpointOverride(URI.create(this.awsStackEndpoint));
        }
        return sqsClientBuilder.build();
    }

    public void run() {
        boolean running = true;
        int counter = 0;

        while (running) {
            if(counter % 10 == 0){
                System.out.println("-----> (Ping) Loop iteration: " + counter);
            }

            // --> 1. Update Status / break
            running = this.update_status();
            if(!running){
                System.out.println("--> PING CONSUMER STOPPED FROM STOP MESSAGE");
                break;
            }

            // --> 2. Message operations
            this.message_loop();

            // --> 3. Check timers / break
            running = this.check_timers();
            if(!running){
                System.out.println("--> PING CONSUMER STOPPED FROM TIMERS");
                break;
            }

            counter++;
        }

        this.shutdown_message();
        System.out.println("--> STATUS CONSUMER FINISHED");
    }





//   _   _           _       _         _____ _        _
//  | | | |         | |     | |       /  ___| |      | |
//  | | | |_ __   __| | __ _| |_ ___  \ `--.| |_ __ _| |_ _   _ ___
//  | | | | '_ \ / _` |/ _` | __/ _ \  `--. \ __/ _` | __| | | / __|
//  | |_| | |_) | (_| | (_| | ||  __/ /\__/ / || (_| | |_| |_| \__ \
//   \___/| .__/ \__,_|\__,_|\__\___| \____/ \__\__,_|\__|\__,_|___/
//        | |
//        |_|


    private boolean update_status() {
        while(!this.mainConsumerQueue.isEmpty()) {
            Map<String, String> msgContents = this.mainConsumerQueue.poll();

            // --> CONTAINER STOP CONDITION
            if(msgContents.containsKey("controller")){
                if (msgContents.get("controller").equals("exit")){
                    return false;
                }
            }

            // --> GA STOP / UPDATE STATUS
            for(String key: msgContents.keySet()){
                if(msgContents.get(key).equals("exit")){
                    this.statusStore.remove(key);
                }
                else{
                    this.statusStore.put(key, msgContents.get(key));
                }
            }
        }
        return true;
    }


//    ___  ___                                 _   _                 _ _ _
//    |  \/  |                                | | | |               | | (_)
//    | .  . | ___  ___ ___  __ _  __ _  ___  | |_| | __ _ _ __   __| | |_ _ __   __ _
//    | |\/| |/ _ \/ __/ __|/ _` |/ _` |/ _ \ |  _  |/ _` | '_ \ / _` | | | '_ \ / _` |
//    | |  | |  __/\__ \__ \ (_| | (_| |  __/ | | | | (_| | | | | (_| | | | | | | (_| |
//    \_|  |_/\___||___/___/\__,_|\__, |\___| \_| |_/\__,_|_| |_|\__,_|_|_|_| |_|\__, |
//                                 __/ |                                          __/ |
//                                |___/

    private void message_loop(){

        List<Message> messages = this.getMessages();

        List<Map<String, String>> messagesContents = new ArrayList<>();
        for (Message msg : messages) {
            HashMap<String, String> msgContents = this.processMessage(msg, true);
            messagesContents.add(msgContents);
        }

        for (Map<String, String> msgContents : messagesContents) {
            this.msgTypePing(msgContents);
        }

        this.deleteMessages(messages);

    }

    private List<Message> getMessages() {
        int waitTimeSeconds = 1;
        int maxMessages = 1;
        ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                .queueUrl(this.pingRequestQueue)
                .waitTimeSeconds(waitTimeSeconds)
                .maxNumberOfMessages(maxMessages)
                .attributeNames(QueueAttributeName.ALL)
                .messageAttributeNames("All")
                .build();
        return this.sqs_client.receiveMessage(receiveMessageRequest).messages();
    }

    private HashMap<String, String> processMessage(Message msg, boolean printInfo) {
        HashMap<String, String> contents = new HashMap<>();
        contents.put("body", msg.body());
        for (String key : msg.messageAttributes().keySet()) {
            contents.put(key, msg.messageAttributes().get(key).stringValue());
        }
        if (printInfo) {
            System.out.println("\n--------------- SQS MESSAGE ---------------");
            System.out.println("--------> BODY: " + msg.body());
            for (String key : msg.messageAttributes().keySet()) {
                System.out.println("---> ATTRIBUTE: " + key + " - " + msg.messageAttributes().get(key).stringValue());
            }
            System.out.println("-------------------------------------------\n");
        }
        // this.consumerSleep(5);
        return contents;
    }

    private void msgTypePing(Map<String, String> msgContents) {
        // Update ping timers
        this.lastPingTime = System.currentTimeMillis();

        final Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        messageAttributes.put("msgType",
                MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue("pingAck")
                        .build()
        );
        for(String key: this.statusStore.keySet()){
            messageAttributes.put(key,
                    MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(this.gson.toJson(this.statusStore.get(key)))
                            .build()
            );
        }
        this.sqs_client.sendMessage(SendMessageRequest.builder()
                .queueUrl(this.pingResponseQueue)
                .messageBody("pingAck")
                .messageAttributes(messageAttributes)
                .delaySeconds(0)
                .build());
    }

    private void deleteMessages(List<Message> messages) {
        for (Message message : messages) {
            DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                    .queueUrl(this.pingRequestQueue)
                    .receiptHandle(message.receiptHandle())
                    .build();
            this.sqs_client.deleteMessage(deleteMessageRequest);
        }
    }



//     _____ _           _      _
//    /  ___| |         | |    | |
//    \ `--.| |__  _   _| |_ __| | _____      ___ __
//     `--. \ '_ \| | | | __/ _` |/ _ \ \ /\ / / '_ \
//    /\__/ / | | | |_| | || (_| | (_) \ V  V /| | | |
//    \____/|_| |_|\__,_|\__\__,_|\___/ \_/\_/ |_| |_|

    private void shutdown_message(){
        System.out.println("--> SHUTTING DOWN PING CONSUMER");
        Map<String, String> shutdown_msg = new HashMap<>();
        shutdown_msg.put("msgType", "exit");
        this.mainConsumerQueueResponse.add(shutdown_msg);
    }

    private boolean check_timers() {
        return System.currentTimeMillis() - this.lastPingTime <= 60 * 60 * 1000;
    }


}