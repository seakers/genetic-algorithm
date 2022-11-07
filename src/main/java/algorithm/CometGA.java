package algorithm;

import algorithm.search.AdaptiveSearch;
import algorithm.search.adaptive.AdaptiveCrossover;
import algorithm.search.adaptive.AdaptiveArchitecture;
import algorithm.search.adaptive.AdaptiveProblem;
import com.algorithm.InitialPopulationQuery;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import database.DatabaseAPI;
import org.moeaframework.algorithm.EpsilonMOEA;
import org.moeaframework.algorithm.NSGAII;
import org.moeaframework.core.*;
import org.moeaframework.core.comparator.ChainedComparator;
import org.moeaframework.core.comparator.CrowdingComparator;
import org.moeaframework.core.comparator.ParetoDominanceComparator;
import org.moeaframework.core.comparator.ParetoObjectiveComparator;
import org.moeaframework.core.operator.InjectedInitialization;
import org.moeaframework.core.operator.TournamentSelection;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class CometGA implements Runnable {

    public DatabaseAPI databaseAPI;
    public Gson gson = new Gson();
    public ConcurrentLinkedQueue<String> privateQueue;
    public ConcurrentLinkedQueue<Map<String, String>> pingQueue;

    public SqsClient sqs;
    public String eval_queue = System.getenv("EVAL_REQUEST_URL");



    // ------- MOEA Framework -------
    public boolean is_stopped;
    public HashMap<String, String> gaParameters;
    public List<Solution> solutions;
    public AdaptiveProblem gaProblem;
    public Algorithm gaAlgorithm;
    // ------------------------------



    public CometGA(DatabaseAPI databaseAPI, HashMap<String, String> gaParameters, ConcurrentLinkedQueue<String> privateQueue, ConcurrentLinkedQueue<Map<String, String>> pingQueue){
        this.databaseAPI = databaseAPI;
        this.gaParameters = gaParameters;
        this.sqs = SqsClient.builder().region(Region.US_EAST_2).build();
        this.privateQueue = privateQueue;
        this.pingQueue = pingQueue;
        this.gaProblem = new AdaptiveProblem(this.databaseAPI);
        this.is_stopped = false;
    }


    // ----------------------
    // --- INITIALIZATION ---
    // ----------------------


    public void initializeNSGAII(){
        this.buildInitialSolutions();
        InjectedInitialization initialization = new InjectedInitialization(this.gaProblem, this.solutions.size(), this.solutions);

        TournamentSelection selection = new TournamentSelection(2,
                new ChainedComparator(
                        new ParetoDominanceComparator(),
                        new CrowdingComparator()
                ));

        Variation crossover = new AdaptiveCrossover(this.databaseAPI);

        this.gaAlgorithm = new NSGAII(
                this.gaProblem,
                new NondominatedSortingPopulation(),
                null,
                selection,
                crossover,
                initialization
        );
    }

    public void initializeEMOEA() {
        this.buildInitialSolutions();
        InjectedInitialization initialization = new InjectedInitialization(this.gaProblem, this.solutions.size(), this.solutions);
        Population population = new Population();


        double[] epsilonDouble = new double[]{0.001, 1};
        EpsilonBoxDominanceArchive archive = new EpsilonBoxDominanceArchive(epsilonDouble);


        ChainedComparator comp = new ChainedComparator(new ParetoObjectiveComparator());
        TournamentSelection selection = new TournamentSelection(2, comp);
        Variation crossover = new AdaptiveCrossover(this.databaseAPI);
        this.gaAlgorithm = new EpsilonMOEA(this.gaProblem, population, archive, selection, crossover, initialization);
    }

    public void buildInitialSolutions(){
        System.out.println("\n-------------------------------------------------------- INITIAL SOLUTIONS: " );

        int initialPopSize = Integer.parseInt(this.gaParameters.get("popSize")) ;
        this.solutions = new ArrayList<>(initialPopSize);

        List<InitialPopulationQuery.Item> items = this.databaseAPI.getInitialPopulation();
        if(items.size() < initialPopSize){
            for(int x = 0; x < initialPopSize; x++){
                if(x < items.size()){
                    this.solutions.add(new AdaptiveArchitecture(this.databaseAPI, items.get(x).representation()));
                }
                else{
                    AdaptiveArchitecture solution = new AdaptiveArchitecture(this.databaseAPI);
                    solution.evaluate("Initial Pop: " + x).syncDesignWithUserDataset();
                    this.solutions.add(solution);
                    this.updatePingQueue();
                    this.checkPrivateQueue();
                    if(this.is_stopped){
                        this.updatePingOnExit();
                        return;
                    }
                }

            }
        }
        else{
            for(int x = 0; x < initialPopSize; x++){
                this.solutions.add(new AdaptiveArchitecture(this.databaseAPI, items.get(x).representation()));
            }
        }
        this.updatePingQueue();
    }






    // -----------
    // --- RUN ---
    // -----------


    public void run() {

        // --> Check for stop condition during initialization
        this.initializeNSGAII();
        if(this.is_stopped){
            this.updatePingOnExit();
            return;
        }


        ExecutorService pool = Executors.newFixedThreadPool(1);
        CompletionService<Algorithm> ecs  = new ExecutorCompletionService<>(pool);

        AdaptiveSearch search = new AdaptiveSearch(this.gaAlgorithm, this.databaseAPI, Integer.parseInt(this.gaParameters.get("maxEvals")), Integer.parseInt(this.gaParameters.get("popSize")), this.privateQueue, this.pingQueue);
        ecs.submit(search);

        try {
            Algorithm alg = ecs.take().get();
        } catch (InterruptedException | ExecutionException ex) {
            ex.printStackTrace();
        }
        this.updatePingOnExit();
    }


    // ------------
    // --- PING ---
    // ------------

    private void updatePingQueue(){
        JsonObject message = new JsonObject();
        HashMap<String, String> message_wrapper = new HashMap<>();
        message.addProperty("status", "Initializing");
        message.add("objectives", this.gson.toJsonTree(this.databaseAPI.objective_ids));
        message.add("population", this.getPopulationDesigns());
        message.addProperty("parameters", this.gson.toJson(this.gaParameters));
        message_wrapper.put(this.databaseAPI.ga_id, this.gson.toJson(message));
        this.pingQueue.add(message_wrapper);
    }

    private JsonArray getPopulationDesigns(){
        JsonArray designs = new JsonArray();
        for(Solution solution_cast: this.solutions){
            AdaptiveArchitecture solution = (AdaptiveArchitecture) solution_cast;
            designs.add(solution.getDesignJsonString());
        }
        return designs;
    }

    private void updatePingOnExit(){
        HashMap<String, String> message_wrapper = new HashMap<>();
        message_wrapper.put(this.databaseAPI.ga_id, "exit");
        this.pingQueue.add(message_wrapper);
    }


    // ---------------
    // --- HELPERS ---
    // ---------------

    private void checkPrivateQueue(){
        if(!this.privateQueue.isEmpty()){
            while (!this.privateQueue.isEmpty()) {
                String msgContents = this.privateQueue.poll();
                if (msgContents.equals("stop")) {
                    this.is_stopped = true;
                }
            }
        }
    }


}
