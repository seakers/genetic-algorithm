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

import java.io.IOException;
import java.util.concurrent.Callable;

public abstract class AbstractInteractiveSearch implements Callable<org.moeaframework.core.Algorithm> {
    private final Algorithm alg;
    private final TypedProperties properties;
    private final String id;
    private boolean isStopped;
    private Connection mqConnection;
    private Channel mqChannel;
    private String receiveQueue;
    private String sendQueue;

    public AbstractInteractiveSearch(Algorithm alg, TypedProperties properties, String id) {
        this.alg = alg;
        this.properties = properties;
        this.id = id;
        this.isStopped = false;
        receiveQueue = id + "_brainga";
        sendQueue = id + "_gabrain";

        System.out.println("---> Send brain msg queue: " +  sendQueue);
        System.out.println("---> Receive queue: " +  receiveQueue);

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(System.getenv("RABBITMQ_HOST"));
        try  {
            mqConnection = factory.newConnection();
            mqChannel = mqConnection.createChannel();
            mqChannel.queueDeclare(receiveQueue, false, false, false, null);
            mqChannel.queueDeclare(sendQueue, false, false, false, null);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
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


            // GABE: try to get the processed architecture back from the design evaluator
            try {

                // GABE: get processed architecture back from vassar
                GetResponse message = mqChannel.basicGet(receiveQueue, true);
                while (message != null) {
                    String body = new String(message.getBody(), "UTF-8");
                    if (body.equals("close")) {
                        this.isStopped = true;
                    }
                    if (body.equals("ping")) {
                        lastPingTime = System.currentTimeMillis();
                    }
                    message = mqChannel.basicGet(receiveQueue, true);
                }
            }
            catch (IOException e) {
                e.printStackTrace();
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
                    System.out.println("---> Sending new arch!");
                    newSol.setAttribute("NFE", alg.getNumberOfEvaluations());


                    // Send the new architectures through REDIS
                    // But first, turn it into something easier in JSON
                    JsonElement jsonArch = getJSONArchitecture(newSol);
                    JsonObject messageBack = new JsonObject();
                    messageBack.add("type", new JsonPrimitive("new_arch"));
                    messageBack.add("data", jsonArch);
                    try {
                        mqChannel.basicPublish("", sendQueue, null, messageBack.toString().getBytes());
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                else{
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
