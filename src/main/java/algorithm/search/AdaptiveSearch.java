package algorithm.search;

import algorithm.search.problems.Adaptive.AdaptiveArchitecture;
import com.algorithm.ProblemDecisionsQuery;
import com.algorithm.ProblemObjectivesQuery;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import database.DatabaseAPI;
import org.moeaframework.Analyzer;
import org.moeaframework.algorithm.AbstractEvolutionaryAlgorithm;
import org.moeaframework.analysis.collector.Accumulator;
import org.moeaframework.core.Algorithm;
import org.moeaframework.core.NondominatedPopulation;
import org.moeaframework.core.Population;
import org.moeaframework.core.Solution;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AdaptiveSearch  implements Callable<Algorithm> {


    public Gson gson = new Gson();
    public Algorithm alg;
    public boolean is_stopped;
    public DatabaseAPI databaseAPI;
    public int maxEvals;
    public int popSize;
    public ConcurrentLinkedQueue<String> privateQueue;
    public ConcurrentLinkedQueue<Map<String, String>> pingQueue;


    // --> Algorithm Analysis
    private Analyzer analyzer;
    private Accumulator accumulator;


    public AdaptiveSearch(Algorithm alg, DatabaseAPI databaseAPI, int maxEvals, int popSize, ConcurrentLinkedQueue<String> privateQueue, ConcurrentLinkedQueue<Map<String, String>> pingQueue){
        this.alg = alg;
        this.privateQueue = privateQueue;
        this.pingQueue = pingQueue;
        this.maxEvals = maxEvals;
        this.popSize = popSize;
        this.databaseAPI = databaseAPI;
        this.is_stopped = false;
        this.buildAnalyzer();
    }

    private void buildAnalyzer(){

        // --> 1. Get problem objectives
        List<ProblemObjectivesQuery.Item> items = this.databaseAPI.getProblemObjectives();

        // --> 2. Create: Ideal Point / Reference Point
        double[] idealPoint = new double[this.databaseAPI.getNumObjectives()];
        double[] refPoint   = new double[this.databaseAPI.getNumObjectives()];
        for(int x = 0; x < items.size(); x++){
            ProblemObjectivesQuery.Item item = items.get(x);
            ArrayList<Double> bounds = this.parseBounds(item.bounds());
            if(item.optimization().equals("min")){
                idealPoint[x] = 0.0;
                refPoint[x] = bounds.get(1);
            }
            else if(item.optimization().equals("max")){
                idealPoint[x] = bounds.get(1) * -1.0;
                refPoint[x] = bounds.get(0);
            }
        }

        System.out.println("------> IDEAL POINT: " + Arrays.toString(idealPoint));
        System.out.println("--> REFERENCE POINT: " + Arrays.toString(refPoint));

        // --> 3. Create: Analyzer / Accumulator
        this.analyzer = new Analyzer()
                .withProblem(this.alg.getProblem())
                .withIdealPoint(idealPoint)
                .withReferencePoint(refPoint)
                .includeHypervolume()
                .includeAdditiveEpsilonIndicator();
        this.accumulator = new Accumulator();
    }

    private ArrayList<Double> parseBounds(String array){
        ArrayList<Double> result = new ArrayList<>();
        String substr = array.substring(1, array.length()-1);
        List<String> items = Arrays.asList(substr.split("\\s*,\\s*"));
        for(String item: items){
            result.add(Double.parseDouble(item));
        }
        return result;
    }


    @Override
    public Algorithm call(){

        this.alg.step();
        this.updateAnalysis();


        Population current_pop = new Population(((AbstractEvolutionaryAlgorithm)this.alg).getArchive());

        this.checkPrivateQueue();
        while (!this.alg.isTerminated() && (this.alg.getNumberOfEvaluations() < this.maxEvals) && !is_stopped) {

            // --> Step algorithm
            alg.step();

            // --> Get new population / update
            Population new_pop = ((AbstractEvolutionaryAlgorithm)alg).getArchive();
            List<Solution> newDesigns = this.getNewDesigns(current_pop, new_pop);
            if(!newDesigns.isEmpty()){
                System.out.println("--> NEW DESIGNS FOUND: " + newDesigns.size());
                // --> Insert design in user dataset
                for(Solution design_cast: newDesigns){
                    AdaptiveArchitecture design = (AdaptiveArchitecture) design_cast;
                    design.syncDesignWithUserDataset();
                }
            }
            current_pop = new Population(new_pop);

            // --> Update Analysis
            this.updateAnalysis();

            // --> Update ping queue
            this.updatePingQueue();

            // --> Check external conditions for exiting
            this.checkPrivateQueue();
        }

        return alg;
    }

    private void updatePingQueue(){
        JsonObject message = new JsonObject();
        HashMap<String, String> message_wrapper = new HashMap<>();
        message.addProperty("hv_history", this.accumulator.toCSV());
        message.addProperty("nfe", this.alg.getNumberOfEvaluations());
        message.add("population", this.getPopulationDesigns());
        message.addProperty("problem_id", this.databaseAPI.problem_id);
        message.addProperty("dataset_id", this.databaseAPI.user_dataset_id);
        message.add("objectives", this.gson.toJsonTree(this.databaseAPI.objective_ids));
        message_wrapper.put(this.databaseAPI.ga_id, this.gson.toJson(message));
        this.pingQueue.add(message_wrapper);
    }

    private JsonArray getPopulationDesigns(){
        JsonArray designs = new JsonArray();
        NondominatedPopulation current_pop = ((AbstractEvolutionaryAlgorithm) this.alg).getArchive();
        for (Solution solution_cast : current_pop) {
            AdaptiveArchitecture solution = (AdaptiveArchitecture) solution_cast;
            designs.add(solution.getDesignJsonString());
        }
        return designs;
    }


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

    private void updateAnalysis(){
        this.analyzer.add("popComet", this.alg.getResult());
        int num_evals = this.alg.getNumberOfEvaluations();
        if(this.analyzer.getAnalysis().get("popComet") != null){
            double current_hv = this.analyzer.getAnalysis().get("popComet").get("Hypervolume").getMax();
            this.accumulator.add("NFE", (num_evals));
            this.accumulator.add("HV", current_hv);
        }
    }



    public List<Solution> getNewDesigns(Population old_pop, Population new_pop){
        ArrayList<Solution> newDesigns = new ArrayList<>();
        for (int i = 0; i < new_pop.size(); ++i){
            Solution newSol = new_pop.get(i);
            if(!old_pop.contains(newSol)){
                newDesigns.add(newSol);
            }
        }
        return newDesigns;
    }


}
