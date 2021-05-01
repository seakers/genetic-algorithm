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
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;

import java.net.URI;


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
        String  requestUrl           = System.getenv("GA_REQUEST_URL");
        String  responseUrl          = System.getenv("GA_RESPONSE_URL");
        String  awsStackEndpoint     = System.getenv("AWS_STACK_ENDPOINT");
        String  regionName           = System.getenv("REGION");
        String  apolloUrl            = System.getenv("APOLLO_URL");
        int     messageRetrievalSize = Integer.parseInt(System.getenv("MESSAGE_RETRIEVAL_SIZE"));
        int     messageQueryTimeout  = Integer.parseInt(System.getenv("MESSAGE_QUERY_TIMEOUT"));


        Region region;
        if(regionName.equals("US_EAST_1")){
            region = Region.US_EAST_1;
        }
        else if(regionName.equals("US_EAST_2")){
            region = Region.US_EAST_2;
        }
        else{
            region = Region.US_EAST_2;
        }

        System.out.println("\n------------------ ALGORITHM INIT ------------------");
        System.out.println("----------> REQUEST QUEUE URL: " + requestUrl);
        System.out.println("-----------> RESPONSE QUEUE URL: " + responseUrl);
        System.out.println("---------------> HASURA URL: " + apolloUrl);
        System.out.println("---------> AWS ENDPOINT URL: " + awsStackEndpoint);
        System.out.println("-------------------> REGION: " + regionName);
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


        SqsClientBuilder sqsClientBuilder = SqsClient.builder()
                                                     .region(region);
        if (awsStackEndpoint != null) {
            sqsClientBuilder.endpointOverride(URI.create(awsStackEndpoint));
        }
        final SqsClient sqsClient = sqsClientBuilder.build();


        Consumer consumer = new Consumer.Builder(sqsClient)
                                        .setAwsStackEndpoint(awsStackEndpoint)
                                        .setRegion(region)
                                        .debug(debug)
                                        .setMessageQueryTimeout(messageQueryTimeout)
                                        .setMessageRetrievalSize(messageRetrievalSize)
                                        .setRequestQueueUrl(requestUrl)
                                        .setResponseQueueUrl(responseUrl)
                                        .setApolloUrl(apolloUrl)
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
