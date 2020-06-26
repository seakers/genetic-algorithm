package app;




//   _____                 _   _                 _                  _ _   _
//  / ____|               | | (_)          /\   | |                (_) | | |
// | |  __  ___ _ __   ___| |_ _  ___     /  \  | | __ _  ___  _ __ _| |_| |__  _ __ ___
// | | |_ |/ _ \ '_ \ / _ \ __| |/ __|   / /\ \ | |/ _` |/ _ \| '__| | __| '_ \| '_ ` _ \
// | |__| |  __/ | | |  __/ |_| | (__   / ____ \| | (_| | (_) | |  | | |_| | | | | | | | |
//  \_____|\___|_| |_|\___|\__|_|\___| /_/    \_\_|\__, |\___/|_|  |_|\__|_| |_|_| |_| |_|
//                                                  __/ |
//                                                 |___/


import sqs.Consumer;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;

import java.net.URI;

import sqs.Consumer;


public class App {

    public static void main(String[] args) {


//  _____ _   _ _____ _______
// |_   _| \ | |_   _|__   __|
//   | | |  \| | | |    | |
//   | | | . ` | | |    | |
//  _| |_| |\  |_| |_   | |
// |_____|_| \_|_____|  |_|
//


        boolean debug                = Boolean.parseBoolean(System.getenv("DEBUG"));
        String  queueUrl             = System.getenv("INPUT_QUEUE_URL");
        String  vassarUrl            = System.getenv("EVAL_QUEUE_URL");
        String  aws_stack_endpoint   = System.getenv("AWS_STACK_ENDPOINT");
        String  region_name          = System.getenv("REGION");
        String  apolloUrl            = System.getenv("APOLLO_URL");
        int     messageRetrievalSize = Integer.parseInt(System.getenv("MESSAGE_RETRIEVAL_SIZE"));
        int     messageQueryTimeout  = Integer.parseInt(System.getenv("MESSAGE_QUERY_TIMEOUT"));


        Region region;
        if(region_name.equals("US_EAST_1")){
            region = Region.US_EAST_1;
        }
        else if(region_name.equals("US_EAST_2")){
            region = Region.US_EAST_2;
        }
        else{
            region = Region.US_EAST_2;
        }

        System.out.println("\n------------------ ALGORITHM INIT ------------------");
        System.out.println("----------> INPUT QUEUE URL: " + queueUrl);
        System.out.println("-----------> EVAL QUEUE URL: " + vassarUrl);
        System.out.println("---------------> HASURA URL: " + apolloUrl);
        System.out.println("---------> AWS ENDPOINT URL: " + aws_stack_endpoint);
        System.out.println("-------------------> REGION: " + region_name);
        System.out.println("---> MESSAGE RETRIEVAL SIZE: " + messageRetrievalSize);
        System.out.println("----> MESSAGE QUERY TIMEOUT: " + messageQueryTimeout);
        System.out.println("--------------------> DEBUG: " + debug);
        System.out.println("-------------------------------------------------------\n");



//  _           _ _     _
// | |         (_) |   | |
// | |__  _   _ _| | __| |
// | '_ \| | | | | |/ _` |
// | |_) | |_| | | | (_| |
// |_.__/ \__,_|_|_|\__,_|
//


        SqsClient sqsClient = SqsClient.builder()
                            .region(region)
                            .endpointOverride(URI.create(aws_stack_endpoint))
                            .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
                            .build();


        Consumer consumer = new Consumer.Builder(sqsClient)
                                        .setAwsStackEndpoint(aws_stack_endpoint)
                                        .setRegion(region)
                                        .debug(debug)
                                        .setMessageQueryTimeout(messageQueryTimeout)
                                        .setMessageRetrievalSize(messageRetrievalSize)
                                        .setQueueUrl(queueUrl)
                                        .setApolloUrl(apolloUrl)
                                        .setVassarQueueUrl(vassarUrl)
                                        .build();


        // RUN CONSUMER
        Thread consumer_thread = new Thread(consumer);
        consumer_thread.start();




        try{
            consumer_thread.join();
        }
        catch(Exception e){
            System.out.println("---> Error joining genetic algorithm thread");
        }

    }

}
