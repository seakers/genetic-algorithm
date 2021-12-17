package algorithm;

import algorithm.search.BinaryInputInteractiveSearch;
import algorithm.search.operators.ApplyFeature;
import algorithm.search.operators.EitherVariation;
import algorithm.search.problems.Assigning.AssigningArchitecture;
import algorithm.search.problems.Assigning.AssigningProblem;
import com.algorithm.InstrumentCountQuery;
import com.algorithm.OrbitCountQuery;
import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.rx2.Rx2Apollo;
import io.reactivex.Observable;
import org.moeaframework.algorithm.EpsilonMOEA;
import org.moeaframework.core.*;
import org.moeaframework.core.comparator.ChainedComparator;
import org.moeaframework.core.comparator.ParetoObjectiveComparator;
import org.moeaframework.core.operator.CompoundVariation;
import org.moeaframework.core.operator.InjectedInitialization;
import org.moeaframework.core.operator.OnePointCrossover;
import org.moeaframework.core.operator.TournamentSelection;
import org.moeaframework.core.operator.binary.BitFlip;
import org.moeaframework.core.variable.BinaryVariable;
import org.moeaframework.util.TypedProperties;

import java.util.*;
import java.util.concurrent.*;

import com.algorithm.ArchitectureQuery;
import seakers.architecture.operators.IntegerUM;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;


public class Algorithm implements Runnable {

    private int maxEvals;
    private int initialPopSize;
    private List<String> objective_list;
    private double crossoverProbability;
    private double mutationProbability;
    private String testedFeature;
    private TypedProperties properties;
    private String vassarQueueUrl;
    private String userResponseUrl;
    private Problem assignmentProblem;
    private List<Solution> solutions;
    private ApolloClient apollo;
    private int numOrbits;
    private int numInstruments;
    private int groupId;
    private int problemId;
    private int datasetId;
    private SqsClient sqs;
    private List<ArchitectureQuery.Item> initialPopulation;
    private ConcurrentLinkedQueue<String> privateQueue;
    private String gaEvalResponseQueue;

    private org.moeaframework.core.Algorithm eMOEA;


    public static class Builder {

        private int maxEvals;
        private int initialPopSize;
        private List<String> objective_list;
        private double crossoverProbability;
        private double mutationProbability;
        private String testedFeature;
        private String vassarQueueUrl;
        private String userResponseUrl;
        private ApolloClient apollo;
        private int numOrbits;
        private int numInstruments;
        private int groupId;
        private int problemId;
        private int datasetId;
        private SqsClient sqs;
        private List<ArchitectureQuery.Item> initialPopulation;
        private ConcurrentLinkedQueue<String> privateQueue;
        private String gaEvalResponseQueue;

        public Builder(String userResponseUrl, String vassarQueueUrl) {
            this.userResponseUrl = userResponseUrl;
            this.vassarQueueUrl = vassarQueueUrl;
        }

        public Builder setApolloClient(ApolloClient client) {
            this.apollo = client;
            return this;
        }

        public Builder setGaEvalResponseQueue(String gaEvalResponseQueue){
            this.gaEvalResponseQueue = gaEvalResponseQueue;
            return this;
        }

        public Builder setObjectiveList(List<String> objective_list){
            this.objective_list = objective_list;
            return this;
        }

        public Builder setMaxEvals(int maxEvals) {
            this.maxEvals = maxEvals;
            return this;
        }

        public Builder setSqsClient(SqsClient sqs) {
            this.sqs = sqs;
            return this;
        }

        public Builder setGroupId(int groupId) {
            this.groupId = groupId;
            return this;
        }

        public Builder setProblemId(int problemId) {
            this.problemId = problemId;
            return this;
        }

        public Builder setDatasetId(int datasetId) {
            this.datasetId = datasetId;
            return this;
        }

        public Builder setCrossoverProbability(double crossoverProbability) {
            this.crossoverProbability = crossoverProbability;
            return this;
        }

        public Builder setMutationProbability(double mutationProbability) {
            this.mutationProbability = mutationProbability;
            return this;
        }

        public Builder setTestedFeature(String testedFeature) {
            this.testedFeature = testedFeature;
            return this;
        }

        public Builder setPrivateQueue(ConcurrentLinkedQueue<String> privateQueue) {
            this.privateQueue = privateQueue;
            return this;
        }

        private List<ArchitectureQuery.Item> getInitialPopulation(int problemId, int datasetId) {
            ArchitectureQuery architectureQuery = ArchitectureQuery.builder()
                    .problem_id(problemId)
                    .dataset_id(datasetId)
                    .build();
            ApolloCall<ArchitectureQuery.Data> apolloCall  = this.apollo.query(architectureQuery);
            Observable<Response<ArchitectureQuery.Data>> observable  = Rx2Apollo.from(apolloCall);
            return observable.blockingFirst().getData().items();
        }

        private int getNumOrbits(int problem_id) {
            OrbitCountQuery orbitQuery = OrbitCountQuery.builder()
                    .problem_id(problem_id)
                    .build();
            ApolloCall<OrbitCountQuery.Data> apolloCall  = this.apollo.query(orbitQuery);
            Observable<Response<OrbitCountQuery.Data>> observable  = Rx2Apollo.from(apolloCall);
            return observable.blockingFirst().getData().item().aggregate().count();
        }

        private int getNumInstr(int problem_id) {
            InstrumentCountQuery orbitQuery = InstrumentCountQuery.builder()
                    .problem_id(problem_id)
                    .build();
            ApolloCall<InstrumentCountQuery.Data> apolloCall  = this.apollo.query(orbitQuery);
            Observable<Response<InstrumentCountQuery.Data>> observable  = Rx2Apollo.from(apolloCall);
            return observable.blockingFirst().getData().item().aggregate().count();
        }

        public Builder getProblemData(int problemId, int datasetId) {

            System.out.println("-----> BUILDING INITIAL SOLUTIONS");

            List<ArchitectureQuery.Item> items  = this.getInitialPopulation(problemId, datasetId);
            this.initialPopulation = items;
            this.initialPopSize    = items.size();
            this.numOrbits         = this.getNumOrbits(problemId);
            this.numInstruments    = this.getNumInstr(problemId);
            return this;
        }

        public Algorithm build() {
            Algorithm build = new Algorithm();

            build.initialPopSize = this.initialPopSize;
            build.maxEvals = this.maxEvals;
            build.crossoverProbability = this.crossoverProbability;
            build.mutationProbability = this.mutationProbability;
            build.testedFeature = this.testedFeature;
            build.userResponseUrl = this.userResponseUrl;
            build.vassarQueueUrl = this.vassarQueueUrl;
            build.apollo = this.apollo;
            build.numOrbits = this.numOrbits;
            build.numInstruments = this.numInstruments;
            build.groupId = this.groupId;
            build.problemId = this.problemId;
            build.datasetId = this.datasetId;
            build.sqs = this.sqs;
            build.initialPopulation = this.initialPopulation;
            build.privateQueue = this.privateQueue;
            build.objective_list = this.objective_list;
            build.gaEvalResponseQueue = this.gaEvalResponseQueue;

            build.properties = new TypedProperties();
            build.properties.setInt("maxEvaluations", this.maxEvals);
            build.properties.setInt("populationSize", this.initialPopSize);
            build.properties.setDouble("crossoverProbability", this.crossoverProbability);
            build.properties.setDouble("mutationProbability", this.mutationProbability);

            build.assignmentProblem = new AssigningProblem(this.sqs, apollo, new int[]{1}, this.numOrbits, this.numInstruments, this.vassarQueueUrl, problemId, datasetId, this.objective_list);
            return build;
        }

    }


    private List<Boolean> stringToBool(String inputs) {
        List<Boolean> bool_list = new ArrayList<>();
        char[] char_inputs = inputs.toCharArray();
        for(char input: char_inputs){
            if(input == '1'){
                bool_list.add(true);
            }
            else{
                bool_list.add(false);
            }
        }
        return bool_list;
    }

    // Change so that it finds the pareto front of all the current architectures
    public void buildInitialSolutions() {
        System.out.println("\n-------------------------------------------------------- INITIAL SOLUTIONS");
        System.out.println(this.objective_list);
        int min_pop_size = 10;

        this.solutions = new ArrayList<>();

        for(ArchitectureQuery.Item item: this.initialPopulation){

            // --> INPUTS
            String        stringInputs      = item.input();

            // --> OBJECTIVES
            HashMap<String, Double> objective_map = new HashMap<>();
            objective_map.put("cost", Double.parseDouble(item.cost().toString()));
            objective_map.put("data_continuity", Double.parseDouble(item.data_continuity().toString()));
            objective_map.put("programmatic_risk", Double.parseDouble(item.programmatic_risk().toString()));
            objective_map.put("fairness", Double.parseDouble(item.fairness().toString()));
            for(ArchitectureQuery.ArchitectureScoreExplanation explanation: item.ArchitectureScoreExplanations()){
                String panel_name = explanation.Stakeholder_Needs_Panel().name();
                objective_map.put(panel_name, -1.0 * Double.parseDouble(explanation.satisfaction().toString()));
            }
            System.out.println("--> OBJECTIVE MAPPER");
            System.out.println(objective_map);



            List<Boolean> inputs            = this.stringToBool(stringInputs);

            AssigningArchitecture newArch = new AssigningArchitecture(new int[]{1}, this.numInstruments, this.numOrbits, this.objective_list.size());

            for (int j = 1; j < newArch.getNumberOfVariables(); ++j) {
                BinaryVariable var = new BinaryVariable(1);
                var.set(0, inputs.get(j-1));
                newArch.setVariable(j, var);
            }

            int counter = 0;
            for(String key: this.objective_list){
                if(objective_map.containsKey(key)){
                    newArch.setObjective(counter, objective_map.get(key));
                }
                else{
                    System.out.println("--> MISSING OBJECTIVE VALUE: " + key);
                    newArch.setObjective(counter, 0);
                }
            }
            newArch.setAlreadyEvaluated(true);
            newArch.setDatabaseId(item.id());
            this.solutions.add(newArch);
        }

        // --> Create random solutions if under min number of designs
        int num_random = min_pop_size - this.solutions.size();
        for(int x = 0; x < num_random; x++){
            List<Boolean> inputs = this.getRandomDesign(this.numOrbits * this.numInstruments);
            AssigningArchitecture newArch = new AssigningArchitecture(new int[]{1}, this.numInstruments, this.numOrbits, this.objective_list.size());
            for (int j = 1; j < newArch.getNumberOfVariables(); ++j) {
                BinaryVariable var = new BinaryVariable(1);
                var.set(0, inputs.get(j-1));
                newArch.setVariable(j, var);
            }
            newArch.setAlreadyEvaluated(false);
            this.solutions.add(newArch);
        }
    }

    public List<Boolean> getRandomDesign(int num_bits){
        ArrayList<Boolean> design = new ArrayList<>();
        Random rand = new Random();
        for(int x = 0; x < num_bits; x++){
            boolean val = rand.nextFloat() < 0.05;
            design.add(val);
        }
        return design;
    }

    public void initialize() {

        // BUILD: this.solutions
        this.buildInitialSolutions();

        // INITIALIZE
        InjectedInitialization initialization = new InjectedInitialization(assignmentProblem, this.solutions.size(), this.solutions);

        // cost, data continuity, programmatic risk, fairness, oceanic, atmosphere, terrestrial
        HashMap<String, Double> obj_epsilon_double = new HashMap<>();
        obj_epsilon_double.put("cost", 1.0);
        obj_epsilon_double.put("data_continuity", 1.0);
        obj_epsilon_double.put("programmatic_risk", .01);
        obj_epsilon_double.put("fairness", .01);
        obj_epsilon_double.put("Oceanic", .0001);
        obj_epsilon_double.put("Atmosphere", .0001);
        obj_epsilon_double.put("Terrestrial", .0001);

        List<Double> epsilonDoubleList = new ArrayList<>();
        for(String obj: this.objective_list){
            epsilonDoubleList.add(obj_epsilon_double.get(obj));
        }
        double[] epsilonDouble = epsilonDoubleList.stream().mapToDouble(d -> d).toArray();
        // double[]                   epsilonDouble = new double[]{1, 1, .01, .01, .0001, .0001, .0001};




        Population                 population    = new Population();
        EpsilonBoxDominanceArchive archive       = new EpsilonBoxDominanceArchive(epsilonDouble);
        ChainedComparator          comp          = new ChainedComparator(new ParetoObjectiveComparator());
        TournamentSelection        selection     = new TournamentSelection(2, comp);

        Variation singlecross      = new OnePointCrossover(crossoverProbability);
        Variation bitFlip          = new BitFlip(mutationProbability);
        Variation integerMutation = new IntegerUM(mutationProbability);
        CompoundVariation var;
        if (this.testedFeature != "") {
            ApplyFeature applyFeature = new ApplyFeature(this.testedFeature, this.numInstruments, this.numOrbits);
            EitherVariation eitherVar = new EitherVariation(singlecross, applyFeature, 0.3, 0.7);
            var = new CompoundVariation(eitherVar, bitFlip, integerMutation);
        }
        else {
            var = new CompoundVariation(singlecross, bitFlip, integerMutation);
        }
        

        // BUILD: MOEA
        this.eMOEA = new EpsilonMOEA(assignmentProblem, population, archive, selection, var, initialization);
        System.out.println("----> eMOEA BUILT");
    }

    public void run() {
        // INITIALIZE
        System.out.println("DEBUG --> Algorithm Response QueueURL: " + this.userResponseUrl);

        this.initialize();

        ExecutorService                                     pool   = Executors.newFixedThreadPool(1);
        CompletionService<org.moeaframework.core.Algorithm> ecs    = new ExecutorCompletionService<>(pool);

        // SUBMIT MOEA
        ecs.submit(new BinaryInputInteractiveSearch(this.eMOEA, this.properties, this.privateQueue, this.sqs, this.apollo, this.userResponseUrl, this.datasetId));

        final Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        messageAttributes.put("msgType",
                MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue("gaStarted")
                        .build()
        );
        this.sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(this.userResponseUrl)
                .messageBody("ga_message")
                .messageAttributes(messageAttributes)
                .delaySeconds(0)
                .build());

        try {
            org.moeaframework.core.Algorithm alg = ecs.take().get();
        } catch (InterruptedException | ExecutionException ex) {
            ex.printStackTrace();
        }




        final Map<String, MessageAttributeValue> messageAttributes2 = new HashMap<>();
        messageAttributes2.put("msgType",
                MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue("gaEnded")
                        .build()
        );
        this.sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(this.userResponseUrl)
                .messageBody("ga_message")
                .messageAttributes(messageAttributes2)
                .delaySeconds(0)
                .build());

        pool.shutdown();

        System.out.println("DONE");
    }


}
