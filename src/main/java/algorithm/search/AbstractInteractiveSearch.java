package algorithm.search;

import com.google.gson.*;
import com.algorithm.DeleteNonImprovingArchitecturesMutation;
import com.algorithm.MarkArchitectureAsImprovingHVMutation;
import com.algorithm.DeleteNonImprovingArchitecturesMutation.Data;
import com.algorithm.MarkArchitectureAsImprovingHVMutation.Update_Architecture_by_pk;
import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.rx2.Rx2Apollo;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.moeaframework.algorithm.AbstractEvolutionaryAlgorithm;
import org.moeaframework.algorithm.EpsilonMOEA;
import org.moeaframework.core.Algorithm;
import org.moeaframework.core.Population;
import org.moeaframework.core.Solution;
import org.moeaframework.core.Variation;
import org.moeaframework.core.operator.CompoundVariation;
import org.moeaframework.util.TypedProperties;

import algorithm.search.operators.ApplyFeature;
import algorithm.search.operators.EitherVariation;
import algorithm.search.problems.Assigning.AssigningArchitecture;
import io.reactivex.Observable;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class AbstractInteractiveSearch implements Callable<Algorithm> {
    private final Algorithm alg;
    private final TypedProperties properties;
    private boolean isStopped;
    private ConcurrentLinkedQueue<String> privateQueue;
    private SqsClient sqsClient;
    private final ApolloClient apollo;
    private String userQueueUrl;
    private int datasetId;
    private EitherVariation featureVariation;

    public AbstractInteractiveSearch(Algorithm alg, TypedProperties properties, ConcurrentLinkedQueue<String> privateQueue, SqsClient sqsClient, ApolloClient apollo, String userQueueUrl, int datasetId, EitherVariation featureVariation) {
        this.alg = alg;
        this.properties = properties;
        this.isStopped = false;
        this.privateQueue = privateQueue;
        this.sqsClient = sqsClient;
        this.apollo = apollo;
        this.userQueueUrl = userQueueUrl;
        this.datasetId = datasetId;
        this.featureVariation = featureVariation;
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
                    String msgString = this.privateQueue.poll();
                    try {
                        JSONParser parser = new JSONParser();
                        JSONObject msgContents = (JSONObject)parser.parse(msgString);
                        String msgType = (String)msgContents.get("type");
                        if (msgType.equals("stop")) {
                            System.out.println("--- Stopping due to external message.");
                            this.isStopped = true;
                        }
                        if (msgType.equals("exploreFeature")) {
                            System.out.println("--- Applying a feature.");
                            ApplyFeature feat = (ApplyFeature)featureVariation.getVar2();
                            String newFeature = (String)msgContents.get("feature");
                            feat.setFeature(newFeature);
                            if (newFeature.equals("")) {
                                featureVariation.setProbabilities(1.0, 0.0);
                            }
                            else {
                                featureVariation.setProbabilities(0.3, 0.7);
                            }
                        }
                        if (msgType.equals("ping")) {
                            lastPingTime = System.currentTimeMillis();
                            // Send ping to thread
                            this.sendPingBack();
                        }
                    }
                    catch (ParseException e) {
                        e.printStackTrace();
                    }
                    
                }
                this.privateQueue.addAll(returnMessages);
            }

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastPingTime > 90*1000) {
                System.out.println("--- Stopping due to expired pings.");
                this.isStopped = true;
            }

            if (this.isStopped) {
                break;
            }

            System.out.println("\n---> Algorithm Step");
            alg.step();

            System.out.println("\n---> Get population");
            Population pop = ((AbstractEvolutionaryAlgorithm) alg).getPopulation();

            // Only send back those architectures that improve the pareto frontier
            System.out.println("\n---> Get new population");
            Population newArchive = ((AbstractEvolutionaryAlgorithm)alg).getArchive();

            // If error happened during evaluation
            if (((AssigningArchitecture)pop.get(pop.size()-1)).getDatabaseId() == -1) {
                System.out.println("--- Stopping due to error during evaluation.");
                break;
            }

            // GABE: this loop process the new architecture from the GA through rabbitmq
            System.out.println("\n---> Compare new to old population");
            for (int i = 0; i < newArchive.size(); ++i) {

                // Check to see if we have a new solution
                Solution newSol = newArchive.get(i);
                boolean alreadyThere = archive.contains(newSol);
                if (!alreadyThere) { // if it is a new solution
                    // Check if it wasn't already in main database
                    if (!((AssigningArchitecture)newSol).getAlreadyExisted()) {
                        System.out.println("---> Sending new arch!");
                        newSol.setAttribute("NFE", alg.getNumberOfEvaluations());

                        // Mark arch as improves_hv so it shows in frontend
                        this.markArchitectureAsImprovingHV(((AssigningArchitecture)newSol).getDatabaseId());
    
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
            }
            System.out.println("---> newArchive (size): "+ newArchive.size());

            // Remove other archs with ga=true, improves_hv=false as not improving
            Data deletionData = this.deleteNonImprovingArchitectures(this.datasetId);

            // Change the archive reference to the new one
            archive = new Population(newArchive);
        }
        System.out.println("--> Cause of ending: isStopped - " + this.isStopped + "; isTerminated - " + alg.isTerminated() + "; numOfEvaluations - " + alg.getNumberOfEvaluations());

        alg.terminate();
        long finishTime = System.currentTimeMillis();
        System.out.println("Done with optimization. Execution time: " + ((finishTime - startTime) / 1000) + "s");

        return alg;
    }

    public Update_Architecture_by_pk markArchitectureAsImprovingHV(int databaseId){
        MarkArchitectureAsImprovingHVMutation archMutation = MarkArchitectureAsImprovingHVMutation.builder()
                                                                    .id(databaseId)
                                                                    .build();
        ApolloCall<MarkArchitectureAsImprovingHVMutation.Data> apolloCall = this.apollo.mutate(archMutation);
        Observable<Response<MarkArchitectureAsImprovingHVMutation.Data>> observable = Rx2Apollo.from(apolloCall);
        return observable.blockingFirst().getData().update_Architecture_by_pk();
    }

    public Data deleteNonImprovingArchitectures(int datasetId){
        DeleteNonImprovingArchitecturesMutation archMutation = DeleteNonImprovingArchitecturesMutation.builder()
                                                                    .dataset_id(datasetId)
                                                                    .build();
        ApolloCall<DeleteNonImprovingArchitecturesMutation.Data> apolloCall = this.apollo.mutate(archMutation);
        Observable<Response<DeleteNonImprovingArchitecturesMutation.Data>> observable = Rx2Apollo.from(apolloCall);
        return observable.blockingFirst().getData();
    }

    public abstract JsonElement getJSONArchitecture(Solution architecture);

    public void sendPingBack() {
        // Notify brain of new GA Architecture for proactive purposes (no need to send arch due to GraphQL)
        final Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        messageAttributes.put("msgType",
                MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue("ping")
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
