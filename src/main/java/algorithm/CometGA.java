package algorithm;

import algorithm.search.AdaptiveSearch;
import algorithm.search.operators.AdaptiveCrossover;
import algorithm.search.problems.Adaptive.AdaptiveArchitecture;
import algorithm.search.problems.Adaptive.AdaptiveProblem;
import com.algorithm.InitialPopulationQuery;
import com.google.gson.JsonObject;
import database.DatabaseAPI;
import org.moeaframework.algorithm.EpsilonMOEA;
import org.moeaframework.core.*;
import org.moeaframework.core.comparator.ChainedComparator;
import org.moeaframework.core.comparator.ParetoObjectiveComparator;
import org.moeaframework.core.operator.InjectedInitialization;
import org.moeaframework.core.operator.TournamentSelection;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class CometGA implements Runnable {

    public DatabaseAPI databaseAPI;
    public HashMap<String, String> gaParameters;
    public AdaptiveProblem gaProblem;
    public ConcurrentLinkedQueue<String> privateQueue;
    public ConcurrentLinkedQueue<Map<String, String>> pingQueue;
    public Algorithm gaAlgorithm;
    public SqsClient sqs;
    public List<Solution> solutions;

    public CometGA(DatabaseAPI databaseAPI, HashMap<String, String> gaParameters, ConcurrentLinkedQueue<String> privateQueue, ConcurrentLinkedQueue<Map<String, String>> pingQueue){
        this.databaseAPI = databaseAPI;
        this.gaParameters = gaParameters;
        this.sqs = SqsClient.builder().region(Region.US_EAST_2).build();
        this.privateQueue = privateQueue;
        this.pingQueue = pingQueue;
        this.gaProblem = new AdaptiveProblem(this.databaseAPI);
    }


    public void buildInitialSolutions(){
        System.out.println("\n-------------------------------------------------------- INITIAL SOLUTIONS");

        int initialPopSize = Integer.parseInt(this.gaParameters.get("popSize")) ;
        this.solutions = new ArrayList<>(initialPopSize);

        List<InitialPopulationQuery.Item> items = this.databaseAPI.getInitialPopulation();
        if(items.size() < initialPopSize){
            for(int x = 0; x < initialPopSize; x++){
                if(x < items.size()){
                    this.solutions.add(new AdaptiveArchitecture(this.databaseAPI, items.get(x).representation()));
                }
                else{
                    this.solutions.add(new AdaptiveArchitecture(this.databaseAPI));
                }
            }
        }
        else{
            for(int x = 0; x < initialPopSize; x++){
                this.solutions.add(new AdaptiveArchitecture(this.databaseAPI, items.get(x).representation()));
            }
        }
    }

    public void initialize() {
        this.buildInitialSolutions();
        InjectedInitialization initialization = new InjectedInitialization(this.gaProblem, this.solutions.size(), this.solutions);
        double[] epsilonDouble = new double[]{0.001, 1};
        Population population    = new Population();
        EpsilonBoxDominanceArchive archive = new EpsilonBoxDominanceArchive(epsilonDouble);
        ChainedComparator comp = new ChainedComparator(new ParetoObjectiveComparator());
        TournamentSelection selection = new TournamentSelection(2, comp);
        Variation crossover = new AdaptiveCrossover(this.databaseAPI);
        this.gaAlgorithm = new EpsilonMOEA(this.gaProblem, population, archive, selection, crossover, initialization);
    }




    public void run() {

        this.initialize();

        ExecutorService pool = Executors.newFixedThreadPool(1);
        CompletionService<Algorithm> ecs  = new ExecutorCompletionService<>(pool);

        AdaptiveSearch search = new AdaptiveSearch(this.gaAlgorithm, this.databaseAPI, Integer.parseInt(this.gaParameters.get("maxEvals")), Integer.parseInt(this.gaParameters.get("popSize")), this.privateQueue, this.pingQueue);
        ecs.submit(search);

        try {
            Algorithm alg = ecs.take().get();
        } catch (InterruptedException | ExecutionException ex) {
            ex.printStackTrace();
        }
    }

}
