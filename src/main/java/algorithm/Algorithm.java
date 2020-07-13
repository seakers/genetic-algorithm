package algorithm;

import algorithm.search.BinaryInputInteractiveSearch;
import algorithm.search.problems.Assigning.AssigningArchitecture;
import algorithm.search.problems.Assigning.AssigningProblem;
import com.algorithm.InstrumentCountQuery;
import com.algorithm.OrbitCountQuery;
import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.rx2.Rx2Apollo;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import com.algorithm.ArchitectureQuery;
import seakers.architecture.operators.IntegerUM;
import software.amazon.awssdk.services.sqs.SqsClient;


public class Algorithm implements Runnable{

    private int maxEvals;
    private int initialPopSize;
    private double crossoverProbability;
    private double mutationProbability;
    private TypedProperties properties;
    private String vassarQueueUrl;
    private Problem assignmentProblem;
    private List<Solution> solutions;
    private int numOrbits;
    private int numInstruments;
    private int problem_id;
    private SqsClient sqs;
    private String ga_id;
    private List<ArchitectureQuery.Item> initialPopulation;

    private org.moeaframework.core.Algorithm eMOEA;


    public static class Builder{

        private int maxEvals;
        private int initialPopSize;
        private double crossoverProbability;
        private double mutationProbability;
        private String vassarQueueUrl;
        private ApolloClient apollo;
        private int numOrbits;
        private int numInstruments;
        private int problem_id;
        private SqsClient sqs;
        private String ga_id;
        private List<ArchitectureQuery.Item> initialPopulation;

        public Builder(String vassarQueueUrl){
            this.vassarQueueUrl = vassarQueueUrl;
        }

        public Builder setApolloClient(ApolloClient client){
            this.apollo = client;
            return this;
        }

        public Builder setGaID(String ga_id){
            this.ga_id = ga_id;
            return this;
        }

        public Builder setMaxEvals(int maxEvals){
            this.maxEvals = maxEvals;
            return this;
        }

        public Builder setSqsClient(SqsClient sqs){
            this.sqs = sqs;
            return this;
        }

        public Builder setProblemId(int problem_id){
            this.problem_id = problem_id;
            return this;
        }

        public Builder setCrossoverProbability(double crossoverProbability){
            this.crossoverProbability = crossoverProbability;
            return this;
        }

        public Builder setMutationProbability(double mutationProbability){
            this.mutationProbability = mutationProbability;
            return this;
        }

        private List<ArchitectureQuery.Item> getInitialPopulation(int problem_id){
            ArchitectureQuery architectureQuery = ArchitectureQuery.builder()
                    .problem_id(problem_id)
                    .build();
            ApolloCall<ArchitectureQuery.Data> apolloCall  = this.apollo.query(architectureQuery);
            Observable<Response<ArchitectureQuery.Data>> observable  = Rx2Apollo.from(apolloCall);
            return observable.blockingFirst().getData().items();
        }

        private int getNumOrbits(int problem_id){
            OrbitCountQuery orbitQuery = OrbitCountQuery.builder()
                    .problem_id(problem_id)
                    .build();
            ApolloCall<OrbitCountQuery.Data> apolloCall  = this.apollo.query(orbitQuery);
            Observable<Response<OrbitCountQuery.Data>> observable  = Rx2Apollo.from(apolloCall);
            return observable.blockingFirst().getData().item().aggregate().count();
        }

        private int getNumInstr(int problem_id){
            InstrumentCountQuery orbitQuery = InstrumentCountQuery.builder()
                    .problem_id(problem_id)
                    .build();
            ApolloCall<InstrumentCountQuery.Data> apolloCall  = this.apollo.query(orbitQuery);
            Observable<Response<InstrumentCountQuery.Data>> observable  = Rx2Apollo.from(apolloCall);
            return observable.blockingFirst().getData().item().aggregate().count();
        }

        public Builder getProblemData(int problem_id){

            System.out.println("-----> BUILDING INITIAL SOLUTIONS");

            List<ArchitectureQuery.Item> items  = this.getInitialPopulation(problem_id);
            this.initialPopulation = items;
            this.initialPopSize    = items.size();
            this.numOrbits         = this.getNumOrbits(problem_id);
            this.numInstruments    = this.getNumInstr(problem_id);
//            this.solutions      = new ArrayList<>(this.initialPopSize);
//
//            for(ArchitectureQuery.Item item: items){
//
//                String        string_inputs = item.input();
//                double        science       = Double.parseDouble(item.science().toString());
//                double        cost          = Double.parseDouble(item.cost().toString());
//                List<Boolean> inputs        = this.stringToBool(string_inputs);
//
//                AssigningArchitecture new_arch = new AssigningArchitecture(new int[]{1}, this.getNumInstr(problem_id), this.getNumOrbits(problem_id), 2);
//
//                for (int j = 1; j < new_arch.getNumberOfVariables(); ++j) {
//                    BinaryVariable var = new BinaryVariable(1);
//                    var.set(0, inputs.get(j-1));
//                    new_arch.setVariable(j, var);
//                }
//                new_arch.setObjective(0, -science);
//                new_arch.setObjective(1, cost);
//                new_arch.setAlreadyEvaluated(true);
//
//                System.out.println("---> SOLUTION: " + string_inputs + " " + science + " " + cost);
//
//                this.solutions.add(new_arch);
//            }
            return this;
        }

        public Algorithm build(){
            Algorithm build = new Algorithm();

            build.ga_id    = this.ga_id;
            build.initialPopSize = this.initialPopSize;
            build.maxEvals = this.maxEvals;
            build.crossoverProbability = this.crossoverProbability;
            build.mutationProbability = this.mutationProbability;
            build.vassarQueueUrl = this.vassarQueueUrl;
            build.numOrbits = this.numOrbits;
            build.numInstruments = this.numInstruments;
            build.problem_id = this.problem_id;
            build.initialPopulation = this.initialPopulation;

            build.properties = new TypedProperties();
            build.properties.setInt("maxEvaluations", this.maxEvals);
            build.properties.setInt("populationSize", this.initialPopSize);
            build.properties.setDouble("crossoverProbability", this.crossoverProbability);
            build.properties.setDouble("mutationProbability", this.mutationProbability);

            build.assignmentProblem = new AssigningProblem(this.sqs, apollo, new int[]{1}, this.numOrbits, this.numInstruments, this.vassarQueueUrl, problem_id);


//            InjectedInitialization initialization = new InjectedInitialization(assignmentProblem, this.solutions.size(), this.solutions);
//
//            double[]                   epsilonDouble = new double[]{0.001, 1};
//            Population                 population    = new Population();
//            EpsilonBoxDominanceArchive archive       = new EpsilonBoxDominanceArchive(epsilonDouble);
//            ChainedComparator          comp          = new ChainedComparator(new ParetoObjectiveComparator());
//            TournamentSelection        selection     = new TournamentSelection(2, comp);
//
//            Variation singlecross      = new OnePointCrossover(crossoverProbability);
//            Variation bitFlip          = new BitFlip(mutationProbability);
//            Variation intergerMutation = new IntegerUM(mutationProbability);
//            CompoundVariation var      = new CompoundVariation(singlecross, bitFlip, intergerMutation);
//
//            build.eMOEA = new EpsilonMOEA(assignmentProblem, population, archive, selection, var, initialization);
//
//
//            System.out.println("----> eMOEA BUILT");
            return build;
        }

    }


    private List<Boolean> stringToBool(String inputs){
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

    public void buildInitialSolutions(){
        System.out.println("\n-------------------------------------------------------- INITIAL SOLUTIONS");

        this.solutions = new ArrayList<>(this.initialPopSize);

        for(ArchitectureQuery.Item item: this.initialPopulation){

            String        string_inputs = item.input();
            double        science       = Double.parseDouble(item.science().toString());
            double        cost          = Double.parseDouble(item.cost().toString());
            List<Boolean> inputs        = this.stringToBool(string_inputs);

            AssigningArchitecture new_arch = new AssigningArchitecture(new int[]{1}, this.numInstruments, this.numOrbits, 2);

            for (int j = 1; j < new_arch.getNumberOfVariables(); ++j) {
                BinaryVariable var = new BinaryVariable(1);
                var.set(0, inputs.get(j-1));
                new_arch.setVariable(j, var);
            }
            new_arch.setObjective(0, -science);
            new_arch.setObjective(1, cost);
            new_arch.setAlreadyEvaluated(true);

            System.out.println("---> SOLUTION: " + string_inputs + " " + science + " " + cost);

            this.solutions.add(new_arch);
        }
    }

    public void initialize(){

        // BUILD: this.solutions
        this.buildInitialSolutions();

        // INITIALIZE
        InjectedInitialization initialization = new InjectedInitialization(assignmentProblem, this.solutions.size(), this.solutions);

        double[]                   epsilonDouble = new double[]{0.001, 1};
        Population                 population    = new Population();
        EpsilonBoxDominanceArchive archive       = new EpsilonBoxDominanceArchive(epsilonDouble);
        ChainedComparator          comp          = new ChainedComparator(new ParetoObjectiveComparator());
        TournamentSelection        selection     = new TournamentSelection(2, comp);

        Variation singlecross      = new OnePointCrossover(crossoverProbability);
        Variation bitFlip          = new BitFlip(mutationProbability);
        Variation intergerMutation = new IntegerUM(mutationProbability);
        CompoundVariation var      = new CompoundVariation(singlecross, bitFlip, intergerMutation);

        // BUILD: MOEA
        this.eMOEA = new EpsilonMOEA(assignmentProblem, population, archive, selection, var, initialization);
        System.out.println("----> eMOEA BUILT");
    }




    public void run(){

        // INITIALIZE
        this.initialize();

        ExecutorService                                     pool   = Executors.newFixedThreadPool(1);
        CompletionService<org.moeaframework.core.Algorithm> ecs    = new ExecutorCompletionService<>(pool);

        // SUBMIT MOEA
        ecs.submit(new BinaryInputInteractiveSearch(this.eMOEA, this.properties, this.ga_id));


        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(System.getenv("RABBITMQ_HOST"));
        String sendbackQueueName  = this.ga_id + "_gabrain";

        try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {
            channel.queueDeclare(sendbackQueueName, false, false, false, null);
            String message = "{ \"type\": \"ga_started\" }";
            channel.basicPublish("", sendbackQueueName, null, message.getBytes("UTF-8"));
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        try {
            org.moeaframework.core.Algorithm alg = ecs.take().get();
        } catch (InterruptedException | ExecutionException ex) {
            ex.printStackTrace();
        }

        // Notify listeners of new architectures in username channel
        try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {
            channel.queueDeclare(sendbackQueueName, false, false, false, null);
            String message = "{ \"type\": \"ga_done\" }";
            channel.basicPublish("", sendbackQueueName, null, message.getBytes("UTF-8"));
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        pool.shutdown();

        System.out.println("DONE");
    }


}
