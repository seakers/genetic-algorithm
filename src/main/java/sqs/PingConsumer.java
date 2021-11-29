package sqs;

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
    private String pingRequestQueue;
    private String pingResponseQueue;
    private SqsClient sqs_client;
    private long lastPingTime;
    private ConcurrentLinkedQueue<Map<String, String>> mainConsumerQueue;
    private ConcurrentLinkedQueue<Map<String, String>> mainConsumerQueueResponse;

    private String internal_status = "";
    private String internal_problem_id = "";
    private String internal_group_id = "";
    private String internal_dataset_id = "";


    public PingConsumer(ConcurrentLinkedQueue<Map<String, String>> mainConsumerQueue, ConcurrentLinkedQueue<Map<String, String>> mainConsumerQueueResponse) {
        this.mainConsumerQueue = mainConsumerQueue;
        this.mainConsumerQueueResponse = mainConsumerQueueResponse;
        this.awsStackEndpoint = System.getenv("AWS_STACK_ENDPOINT");
        this.pingRequestQueue = System.getenv("PING_REQUEST_QUEUE");
        this.pingResponseQueue = System.getenv("PING_RESPONSE_QUEUE");
        this.sqs_client = this.newClient();
        this.lastPingTime = System.currentTimeMillis();
    }

    public SqsClient newClient() {
        String regionName = System.getenv("REGION");
        Region region;
        if (regionName.equals("US_EAST_1")) {
            region = Region.US_EAST_1;
        } else if (regionName.equals("US_EAST_2")) {
            region = Region.US_EAST_2;
        } else {
            region = Region.US_EAST_2;
        }
        SqsClientBuilder sqsClientBuilder = SqsClient.builder()
                .region(region);
        if (this.awsStackEndpoint != null) {
            sqsClientBuilder.endpointOverride(URI.create(this.awsStackEndpoint));
        }
        return sqsClientBuilder.build();
    }

    private boolean update_status() {
        while (!this.mainConsumerQueue.isEmpty()) {
            Map<String, String> msgContents = this.mainConsumerQueue.poll();
            if(msgContents.get("msgType").equals("stop")){
                return false;
            }
            this.internal_status = msgContents.get("STATUS");
            if(this.internal_status.equals("RUNNING")){
                this.internal_problem_id = msgContents.get("PROBLEM_ID");
                this.internal_group_id = msgContents.get("GROUP_ID");
                this.internal_dataset_id = msgContents.get("DATASET_ID");
            }
        }
        return true;
    }

    public void run() {
        boolean running = true;

        while (running) {

            // --> 1. Check queue for messages
            List<Message> messages = this.getMessages();

            // --> 2. Get message contents
            List<Map<String, String>> messagesContents = new ArrayList<>();
            for (Message msg : messages) {
                HashMap<String, String> msgContents = this.processMessage(msg, true);
                messagesContents.add(msgContents);
            }

            // --> 2.5 Update Status
            running = this.update_status();

            // --> 3. Handle Ping
            for (Map<String, String> msgContents : messagesContents) {
                this.lastPingTime = System.currentTimeMillis();
                this.handleStatus(msgContents);
            }

            // --> 4. Delete addressed pings
            this.deleteMessages(messages);

            if(!running){
                break;
            }

            // --> 5. Check timers
            running = this.check_criteria();

        }

        this.shutdown_message();
        System.out.println("--> STATUS CONSUMER FINISHED");
    }

    private void shutdown_message(){
        Map<String, String> shutdown_msg = new HashMap<>();
        shutdown_msg.put("msgType", "exit");
        this.mainConsumerQueueResponse.add(shutdown_msg);
    }

    private boolean check_criteria() {
        if (!this.check_timers() || !this.mainConsumerQueue.isEmpty()) {
            return false;
        }
        return true;
    }

    private boolean check_timers() {
        return System.currentTimeMillis() - this.lastPingTime <= 60 * 60 * 1000;
    }

    private void handleStatus(Map<String, String> msgContents) {
        final Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        messageAttributes.put("msgType",
                MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue("statusAck")
                        .build()
        );
        if (internal_status.equals("READY")) {
            messageAttributes.put("status",
                    MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(this.internal_status)
                            .build()
            );
        } else if (internal_status.equals("RUNNING")) {
            messageAttributes.put("status",
                    MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(this.internal_status)
                            .build()
            );
            messageAttributes.put("PROBLEM_ID",
                    MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(this.internal_problem_id)
                            .build()
            );
            messageAttributes.put("GROUP_ID",
                    MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(this.internal_group_id)
                            .build()
            );
            messageAttributes.put("DATASET_ID",
                    MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(this.internal_dataset_id)
                            .build()
            );
        }
        this.sqs_client.sendMessage(SendMessageRequest.builder()
                .queueUrl(this.pingResponseQueue)
                .messageBody("ga_ping_ack")
                .messageAttributes(messageAttributes)
                .delaySeconds(0)
                .build());
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

    private void deleteMessages(List<Message> messages) {
        for (Message message : messages) {
            DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                    .queueUrl(this.pingRequestQueue)
                    .receiptHandle(message.receiptHandle())
                    .build();
            this.sqs_client.deleteMessage(deleteMessageRequest);
        }

    }

}
