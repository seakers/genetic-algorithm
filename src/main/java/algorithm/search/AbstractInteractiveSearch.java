package algorithm.search;

import com.google.gson.*;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import org.moeaframework.algorithm.AbstractEvolutionaryAlgorithm;
import org.moeaframework.core.Algorithm;
import org.moeaframework.core.Population;
import org.moeaframework.core.Solution;
import org.moeaframework.util.TypedProperties;

import algorithm.search.problems.Assigning.AssigningArchitecture;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class AbstractInteractiveSearch implements Callable<org.moeaframework.core.Algorithm> {
    private final Algorithm alg;
    private final TypedProperties properties;
    private boolean isStopped;
    private ConcurrentLinkedQueue<String> privateQueue;
    private SqsClient sqsClient;
    private String userQueueUrl;

    public AbstractInteractiveSearch(Algorithm alg, TypedProperties properties, ConcurrentLinkedQueue<String> privateQueue, SqsClient sqsClient, String userQueueUrl) {
        this.alg = alg;
        this.properties = properties;
        this.isStopped = false;
        this.privateQueue = privateQueue;
        this.sqsClient = sqsClient;
        this.userQueueUrl = userQueueUrl;
    }

    @Override
    public Algorithm call() {

        int populationSize = (int) properties.getDouble("populationSize", 600);
        int maxEvaluations = (int) properties.getDouble("maxEvaluations", 10000);

        // run the executor using the listener to collect results
        System.out.println("---> Starting " + alg.getClass().getSimpleName() + " on " + alg.getProblem().getName() + " with pop size: " + populationSize);
        alg.step();
        long startTime = System.currentTimeMillis();
        long lastPingTime = System.currentTimeMillis();

        Population archive = new Population(((AbstractEvolutionaryAlgorithm)alg).getArchive());

        while (!alg.isTerminated() && (alg.getNumberOfEvaluations() < maxEvaluations) && !isStopped) {
            // External conditions for stopping
            if (!this.privateQueue.isEmpty()) {
                ArrayList<String> returnMessages = new ArrayList<>();
                while (!this.privateQueue.isEmpty()) {
                    String msgContents = this.privateQueue.poll();
                    if (msgContents.equals("stop")) {
                        this.isStopped = true;
                    }
                    if (msgContents.equals("ping")) {
                        lastPingTime = System.currentTimeMillis();
                    }
                }
                this.privateQueue.addAll(returnMessages);
            }

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastPingTime > 60*1000) {
                this.isStopped = true;
            }

            if (this.isStopped) {
                break;
            }

            System.out.println("\n\n---> Algorithm Step");
            alg.step();

            System.out.println("\n\n---> Get population");
            Population pop = ((AbstractEvolutionaryAlgorithm) alg).getPopulation();

            // Only send back those architectures that improve the pareto frontier
            System.out.println("\n\n---> Get new population");
            Population newArchive = ((AbstractEvolutionaryAlgorithm)alg).getArchive();

            // GABE: this loop process the new architecture from the GA through rabbitmq
            System.out.println("\n\n---> Compare new to old population");
            for (int i = 0; i < newArchive.size(); ++i) {

                // Check to see if we have a new solution
                Solution newSol = newArchive.get(i);
                boolean alreadyThere = archive.contains(newSol);
                if (!alreadyThere) { // if it is a new solution
                    // Check if it wasn't already in main database
                    if (!((AssigningArchitecture)newSol).getAlreadyExisted()) {
                        System.out.println("---> Sending new arch!");
                        newSol.setAttribute("NFE", alg.getNumberOfEvaluations());
    
                        // Notify brain of new GA Architecture for proactive purposes (no need to send arch due to GraphQL)
                        final Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
                        messageAttributes.put("msgType",
                                MessageAttributeValue.builder()
                                        .dataType("String")
                                        .stringValue("newGaArch")
                                        .build()
                        );
                        this.sqsClient.sendMessage(SendMessageRequest.builder()
                                .queueUrl(this.userQueueUrl)
                                .messageBody("ga_message")
                                .messageAttributes(messageAttributes)
                                .delaySeconds(0)
                                .build());
                    }
                }
                else {
                    System.out.println("---> Architecture already there");
                    System.out.println("---> newArchive (size): "+ newArchive.size());
                }
            }

            // Change the archive reference to the new one
            archive = new Population(newArchive);
        }

        alg.terminate();
        long finishTime = System.currentTimeMillis();
        System.out.println("Done with optimization. Execution time: " + ((finishTime - startTime) / 1000) + "s");

        return alg;
    }

    public abstract JsonElement getJSONArchitecture(Solution architecture);
}
