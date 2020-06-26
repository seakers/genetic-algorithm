package sqs;






import algorithm.Algorithm;
import com.apollographql.apollo.ApolloClient;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import okhttp3.OkHttpClient;
import org.moeaframework.core.Solution;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import software.amazon.awssdk.regions.Region;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class Consumer implements Runnable{

    private SqsClient sqsClient;
    private String    queueUrl;
    private String    apolloUrl;
    private String    vassarQueueUrl;
    private boolean   debug;
    private boolean   running;
    private int       messageRetrievalSize;
    private int       messageQueryTimeout;
    private Map<String, Thread> algorithms;
    private Region    region;
    private String    aws_stack_endpoint;

    public static class Builder{

        private SqsClient sqsClient;
        private String    queueUrl;
        private String    vassarQueueUrl;
        private String    apolloUrl;
        private boolean   debug;
        private int       messageRetrievalSize;
        private int       messageQueryTimeout;
        private Region    region;
        private String    aws_stack_endpoint;

        public Builder(SqsClient sqsClient){
            this.sqsClient = sqsClient;
        }

        public Builder setQueueUrl(String queueUrl) {
            this.queueUrl = queueUrl;
            return this;
        }

        public Builder setAwsStackEndpoint(String aws_stack_endpoint){
            this.aws_stack_endpoint = aws_stack_endpoint;
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

        public Builder setVassarQueueUrl(String vassarQueueUrl){
            this.vassarQueueUrl = vassarQueueUrl;
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
            build.queueUrl       = this.queueUrl;
            build.vassarQueueUrl = this.vassarQueueUrl;
            build.debug          = this.debug;
            build.messageRetrievalSize = this.messageRetrievalSize;
            build.messageQueryTimeout  = this.messageQueryTimeout;
            build.region               = this.region;
            build.aws_stack_endpoint   = this.aws_stack_endpoint;
            build.running              = true;
            build.algorithms           = new HashMap<>();

            return build;
        }



    }

    public void consumerSleep(int seconds){
        try                            { TimeUnit.SECONDS.sleep(seconds); }
        catch (InterruptedException e) { e.printStackTrace(); }
    }



    public void run(){
        int counter = 0;


        while(this.running){
            System.out.println("---> Sleep Iteration: " + counter);
            this.consumerSleep(1);

            List<Message> messages = this.getMessages(this.messageRetrievalSize, this.messageQueryTimeout);



            for (Message msg: messages){
                HashMap<String, String> msg_contents = this.processMessage(msg);
                System.out.println(msg_contents);

                if(msg_contents.containsKey("msgType")){
                    String msgType = msg_contents.get("msgType");
                    if(msgType.equals("start_ga")){
                        this.msgTypeStartGa(msg_contents);
                    }
                    else if(msgType.equals("stop_ga")){
                        this.msgTypeStopGa(msg_contents);
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
            this.deleteMessages(messages);
            counter++;
        }

    }


    // ---> ALGORITHMS
    private void msgTypeStartGa(HashMap<String, String> msg_contents){
        deleteAllDoneGAs();

        String maxEvals  = msg_contents.get("maxEvals");
        String crossoverProbability  = msg_contents.get("crossoverProbability");
        String mutationProbability  = msg_contents.get("mutationProbability");
        String group_id  = msg_contents.get("group_id");
        String problem_id  = msg_contents.get("problem_id");
        String ga_id  = msg_contents.get("ga_id");

        System.out.println("\n-------------------- ALGORITHM REQUEST --------------------");
        System.out.println("---------------> MAX EVALS: " + maxEvals);
        System.out.println("---> CROSSOVER PROBABILITY: " + crossoverProbability);
        System.out.println("----> MUTATION PROBABILITY: " + mutationProbability);
        System.out.println("----------------> GROUP ID: " + group_id);
        System.out.println("--------------> PROBLEM ID: " + problem_id);
        System.out.println("--------------> APOLLO URL: " + this.apolloUrl);
        System.out.println("------> AWS STACK ENDPOINT: " + this.aws_stack_endpoint);
        System.out.println("--------> VASSAR QUEUE URL: " + this.vassarQueueUrl);
        System.out.println("-------------------> GA ID: " + ga_id);
        System.out.println("----------------------------------------------------------\n");
        this.consumerSleep(3);

        OkHttpClient http   = new OkHttpClient.Builder().build();
        ApolloClient apollo = ApolloClient.builder().serverUrl(this.apolloUrl).okHttpClient(http).build();

        SqsClient sqsClient = SqsClient.builder()
                .region(this.region)
                .endpointOverride(URI.create(this.aws_stack_endpoint))
                .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                .build();

        Algorithm process = new Algorithm.Builder(this.vassarQueueUrl)
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

    private int msgTypeStopGa(HashMap<String, String> msg_contents){
        String id  = msg_contents.get("ga_id");

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
    private List<Message> getMessages(int maxMessages, int waitTimeSeconds){
        ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                .queueUrl(this.queueUrl)
                .waitTimeSeconds(waitTimeSeconds)
                .maxNumberOfMessages(maxMessages)
                .attributeNames(QueueAttributeName.ALL)
                .messageAttributeNames("All")
                .build();
        return this.sqsClient.receiveMessage(receiveMessageRequest).messages();
    }

    // 2.
    private HashMap<String, String> processMessage(Message msg){
        HashMap<String, String> contents = new HashMap<>();
        contents.put("body", msg.body());
        System.out.println("\n--------------- SQS MESSAGE ---------------");
        System.out.println("--------> BODY: " + msg.body());
        for(String key: msg.messageAttributes().keySet()){
            contents.put(key, msg.messageAttributes().get(key).stringValue());
            System.out.println("---> ATTRIBUTE: " + key + " - " + msg.messageAttributes().get(key).stringValue());
        }
        System.out.println("-------------------------------------------\n");
        // this.consumerSleep(5);
        return contents;
    }

    // 3.
    private void deleteMessages(List<Message> messages){
        for (Message message : messages) {
            DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build();
            this.sqsClient.deleteMessage(deleteMessageRequest);
        }
    }


}
