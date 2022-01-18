package algorithm.search;

import com.algorithm.DeleteNonImprovingArchitecturesMutation;
import com.algorithm.DeleteNonImprovingArchitecturesMutation.Data;
import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.rx2.Rx2Apollo;

import org.moeaframework.algorithm.AbstractEvolutionaryAlgorithm;
import org.moeaframework.core.Algorithm;
import org.moeaframework.core.Population;
import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.BinaryVariable;
import org.moeaframework.util.TypedProperties;

import algorithm.search.problems.Assigning.AssigningArchitecture;
import io.reactivex.Observable;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BulkSearch implements Callable<Algorithm> {
    private final Algorithm alg;
    private final TypedProperties properties;
    private boolean isStopped;
    private ConcurrentLinkedQueue<String> privateQueue;
    private SqsClient sqsClient;
    private final ApolloClient apollo;
    private String userQueueUrl;
    private int datasetId;
    private int problemId;
    private int maxSeconds;

    public BulkSearch(Algorithm alg, TypedProperties properties, ConcurrentLinkedQueue<String> privateQueue, SqsClient sqsClient, ApolloClient apollo, String userQueueUrl, int datasetId, int problemId, int maxSeconds) {
        this.alg = alg;
        this.properties = properties;
        this.isStopped = false;
        this.privateQueue = privateQueue;
        this.sqsClient = sqsClient;
        this.apollo = apollo;
        this.userQueueUrl = userQueueUrl;
        this.datasetId = datasetId;
        this.problemId = problemId;
        this.maxSeconds = maxSeconds;
    }

    @Override
    public Algorithm call() {

        int populationSize = (int) properties.getDouble("populationSize", 600);
        int maxEvaluations = (int) properties.getDouble("maxEvaluations", 10000);

        Path csvFile = null;
        try {
            // Count how many runs of this problem ID + dataset ID have already happened
            Path resultsDir = Path.of("/results");
            int fileCount = 0;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(resultsDir, Integer.toString(problemId) + "_" + Integer.toString(datasetId) + "_*")) {
                for (Path entry: stream) {
                    fileCount += 1;
                }
            }
            csvFile = Path.of("/results/" + Integer.toString(problemId) + "_" + Integer.toString(datasetId) + "_" + Integer.toString(fileCount) + ".csv");
            Files.createFile(csvFile);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        
        // run the executor using the listener to collect results
        System.out.println("---> Starting " + alg.getClass().getSimpleName() + " on " + alg.getProblem().getName() + " with pop size: " + populationSize);
        alg.step();
        long startTime = System.currentTimeMillis();

        Population archive = new Population(((AbstractEvolutionaryAlgorithm)alg).getArchive());

        ArrayList<Solution> allSolutions = new ArrayList<>();
        ArrayList<Long> allSolsTime = new ArrayList<>();
        Population initPop = ((AbstractEvolutionaryAlgorithm) alg).getPopulation();
        for (int i = 0; i < initPop.size(); i++) {
            initPop.get(i).setAttribute("NFE", 0);
            allSolutions.add(initPop.get(i));
            allSolsTime.add(0L);
        }

        while (!alg.isTerminated() && (alg.getNumberOfEvaluations() < maxEvaluations) && !isStopped) {
            // External conditions for stopping
            if (!this.privateQueue.isEmpty()) {
                ArrayList<String> returnMessages = new ArrayList<>();
                while (!this.privateQueue.isEmpty()) {
                    String msgContents = this.privateQueue.poll();
                    if (msgContents.equals("stop")) {
                        System.out.println("--- Stopping due to external message.");
                        this.isStopped = true;
                    }
                }
                this.privateQueue.addAll(returnMessages);
            }

            long currentTime = System.currentTimeMillis();
            if (this.maxSeconds > 0 && currentTime - startTime > this.maxSeconds*1000) {
                System.out.println("--- Stopping from time.");
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

            // If error happened during evaluation
            if (((AssigningArchitecture)pop.get(pop.size()-1)).getDatabaseId() == -1) {
                System.out.println("--- Stopping due to error during evaluation.");
                break;
            }

            // GABE: this loop process the new architecture from the GA through rabbitmq
            System.out.println("\n\n---> Compare new to old population");
            for (int i = 0; i < newArchive.size(); ++i) {

                // Check to see if we have a new solution
                Solution newSol = newArchive.get(i);
                boolean alreadyThere = archive.contains(newSol);
                if (!alreadyThere) { // if it is a new solution
                    // Check if it wasn't already in main database
                    System.out.println("---> Saving new arch!");
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

                    allSolutions.add(newSol);
                    allSolsTime.add((currentTime-startTime)/1000);
                }
                else {
                    System.out.println("---> Architecture already there");
                    System.out.println("---> newArchive (size): "+ newArchive.size());
                }
            }

            // Change the archive reference to the new one
            archive = new Population(newArchive);
        }
        System.out.println("--> Cause of ending: isStopped - " + this.isStopped + "; isTerminated - " + alg.isTerminated() + "; numOfEvaluations - " + alg.getNumberOfEvaluations());

        // Remove all archs from DB, as this is bulk running
        Data deletionData = this.deleteNonImprovingArchitectures(this.datasetId);

        alg.terminate();
        long finishTime = System.currentTimeMillis();
        System.out.println("Done with optimization. Execution time: " + ((finishTime - startTime) / 1000) + "s");

        System.out.println("Saving all found architectures to file " + csvFile.toString());

        try {
            try(BufferedWriter buffer =
                    Files.newBufferedWriter(csvFile,
                                            Charset.defaultCharset(),
                                            StandardOpenOption.WRITE)){
                int iter = 0;
                for(Solution sol: allSolutions) {
                    StringBuilder lineBuilder = new StringBuilder(80);
                    String inputs = "";
                    for (int j = 1; j < sol.getNumberOfVariables(); ++j) {
                        BinaryVariable var = (BinaryVariable)sol.getVariable(j);
                        boolean binaryVal = var.get(0);
                        inputs += binaryVal ? "1" : "0";
                    }
                    lineBuilder.append(inputs);
                    lineBuilder.append(",");
                    lineBuilder.append(-sol.getObjective(0));
                    lineBuilder.append(",");
                    lineBuilder.append(sol.getObjective(1));
                    lineBuilder.append(",");
                    lineBuilder.append(sol.getAttribute("NFE"));
                    lineBuilder.append(",");
                    lineBuilder.append(allSolsTime.get(iter));
                    buffer.append(lineBuilder.toString());
                    buffer.newLine();
                    iter += 1;
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        return alg;
    }

    public Data deleteNonImprovingArchitectures(int datasetId){
        DeleteNonImprovingArchitecturesMutation archMutation = DeleteNonImprovingArchitecturesMutation.builder()
                                                                    .dataset_id(datasetId)
                                                                    .build();
        ApolloCall<DeleteNonImprovingArchitecturesMutation.Data> apolloCall = this.apollo.mutate(archMutation);
        Observable<Response<DeleteNonImprovingArchitecturesMutation.Data>> observable = Rx2Apollo.from(apolloCall);
        return observable.blockingFirst().getData();
    }
}
