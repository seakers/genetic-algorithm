package app;




//   _____                 _   _                 _                  _ _   _
//  / ____|               | | (_)          /\   | |                (_) | | |
// | |  __  ___ _ __   ___| |_ _  ___     /  \  | | __ _  ___  _ __ _| |_| |__  _ __ ___
// | | |_ |/ _ \ '_ \ / _ \ __| |/ __|   / /\ \ | |/ _` |/ _ \| '__| | __| '_ \| '_ ` _ \
// | |__| |  __/ | | |  __/ |_| | (__   / ____ \| | (_| | (_) | |  | | |_| | | | | | | | |
//  \_____|\___|_| |_|\___|\__|_|\___| /_/    \_\_|\__, |\___/|_|  |_|\__|_| |_|_| |_| |_|
//                                                  __/ |
//                                                 |___/


import software.amazon.awssdk.services.ec2.Ec2Client;
import sqs.Consumer;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.regions.Region;

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


        System.out.println("\n------------------ ALGORITHM INIT ------------------");
        System.out.println("------> PING REQUEST QUEUE: " + System.getenv("PING_REQUEST_URL"));
        System.out.println("-----> PING RESPONSE QUEUE: " + System.getenv("PING_RESPONSE_URL"));
        System.out.println("------> PRIV REQUEST QUEUE: " + System.getenv("PRIVATE_REQUEST_URL"));
        System.out.println("-----> PRIV RESPONSE QUEUE: " + System.getenv("PRIVATE_RESPONSE_URL"));
        System.out.println("--------------> HASURA URL: " + System.getenv("APOLLO_URL"));
        System.out.println("-------------------> DEBUG: " + debug);
        System.out.println("-------------------------------------------------------\n");


//  _           _ _     _
// | |         (_) |   | |
// | |__  _   _ _| | __| |
// | '_ \| | | | | |/ _` |
// | |_) | |_| | | | (_| |
// |_.__/ \__,_|_|_|\__,_|
//


        // --> SQS Client
        SqsClientBuilder sqsClientBuilder = SqsClient.builder().region(Region.US_EAST_2);
        final SqsClient sqsClient = sqsClientBuilder.build();

        // --> EC2 Client
        Ec2Client ec2Client = Ec2Client.builder()
                .region(Region.US_EAST_2)
                .build();

        Consumer consumer = new Consumer.Builder(sqsClient)
                                        .setEC2Client(ec2Client)
                                        .debug(debug)
                                        .build();

        consumer.run();
    }
}
