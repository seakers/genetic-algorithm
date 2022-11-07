package algorithm.search.adaptive;

import com.algorithm.ProblemDecisionsQuery;
import com.algorithm.ProblemObjectivesQuery;
import com.algorithm.SingleArchitectureQuery;
import com.algorithm.type.Comet_problem_architecture_insert_input;
import com.algorithm.type.Comet_problem_objectivevalue_arr_rel_insert_input;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import database.DatabaseAPI;
import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.BinaryIntegerVariable;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class AdaptiveArchitecture extends Solution {

    public String eval_queue = System.getenv("EVAL_REQUEST_URL");
    public SingleArchitectureQuery.Item architecture = null;
    public Gson gson = new Gson();
    public boolean alreadyEvaluated;
    public boolean alreadyExisted;

    public String representation;

    public DatabaseAPI database_api;

    public Integer architecture_id = -1;
    public Integer architecture_dataset_id = -1;
    public Random rand = new Random();

    public SqsClient sqs = SqsClient.builder().region(Region.US_EAST_2).build();

    public HashMap<Integer, Double> objective_map = new HashMap<>();


    // -------------------
    // --- CONSTRUCTOR ---
    // -------------------

    private AdaptiveArchitecture(Solution solution) {
        super(solution);
        AdaptiveArchitecture solution_cast  = (AdaptiveArchitecture) solution;
        this.database_api = solution_cast.database_api;
        this.alreadyEvaluated = solution_cast.alreadyEvaluated;
        this.alreadyExisted = solution_cast.alreadyExisted;
        this.representation = solution_cast.representation;
        this.rand = solution_cast.rand;
        this.gson = solution_cast.gson;
        this.architecture_id = solution_cast.architecture_id;
        this.architecture_dataset_id = solution_cast.architecture_dataset_id;
        this.objective_map = solution_cast.objective_map;
        this.sqs = solution_cast.sqs;
    }

    public AdaptiveArchitecture(DatabaseAPI database_api, String chromosome){
        super(database_api.getNumDecisions(), database_api.getNumObjectives(), database_api.getNumConstraints());
        this.database_api = database_api;
        this.setInnerVariables();
        this.representation = chromosome;
        this.copy_architecture_db_info();
    }

    public AdaptiveArchitecture(DatabaseAPI database_api){
        super(database_api.getNumDecisions(), database_api.getNumObjectives(), database_api.getNumConstraints());
        this.database_api = database_api;
        this.setInnerVariables();
        this.representation = this.generateRandomDesign();
        this.copy_architecture_db_info();
    }

    private void setInnerVariables(){
        List<ProblemDecisionsQuery.Item> problemDecisions = this.database_api.getProblemDecisions();
        int counter = 0;
        for(int x = 0; x < problemDecisions.size(); x++){
            int num_options = problemDecisions.get(x).alternatives().size();
            BinaryIntegerVariable var = new BinaryIntegerVariable(0, 0, num_options);
            this.setVariable(counter, var);
            counter++;
        }
    }

    private void copy_architecture_db_info(){

        // --> Pulls architecture from any dataset
        List<SingleArchitectureQuery.Item> items = this.database_api.getArchitecture(this.representation);
        if(items.isEmpty()){
            this.alreadyEvaluated = false;
            this.alreadyExisted = false;
            this.architecture_id = -1;
            this.architecture_dataset_id = -1;
            this.architecture = null;
        }
        else{
            SingleArchitectureQuery.Item item = items.get(0);
            this.architecture = item;
            this.alreadyEvaluated = item.evaluation_status();
            this.alreadyExisted = true;
            this.architecture_id = ((BigDecimal) item.id()).intValue();
            this.architecture_dataset_id = ((BigDecimal) item.dataset_id()).intValue();
            for(SingleArchitectureQuery.Objective objective_itr: item.objective()){
                Integer objective_id_itr = ((BigDecimal) objective_itr.objective_id()).intValue();
                Double objective_value_itr = ((BigDecimal) objective_itr.value()).doubleValue();
                this.objective_map.put(objective_id_itr, objective_value_itr);
            }
            this.parseObjectives();
        }
    }




    // ---------------------------
    // --- DATABASE OPERATIONS ---
    // ---------------------------

    // --> No DB design must be loaded yet
    public boolean subscribeToSelf(){
        int timeout = 200;
        int counter = 0;
        try{
            this.copy_architecture_db_info();
            while(!this.alreadyExisted && counter < timeout){
                counter += 1;
                TimeUnit.SECONDS.sleep(2);
                this.copy_architecture_db_info();
            }
        }
        catch(Exception e){
            System.out.println("---> Error evaluating architecture!!!!");
            return false;
        }
        return this.alreadyExisted;
    }


    // --> Design must be loaded from DB
    public void syncDesignWithUserDataset(){
        if(this.architecture_id == -1 || this.architecture_dataset_id == -1){
            System.out.println("--> ERROR, DESIGN NOT EVALUATED");
            return;
        }
        if(this.architecture_dataset_id != this.database_api.user_dataset_id){
            if(!this.database_api.doesUserDesignExist(this.representation)){
                System.out.println("----- (NEW DESIGN) Copying to user dataset: " + this.architecture_dataset_id + " --> " + this.database_api.user_dataset_id);
                int arch_id = this.database_api.copyArchitectureToUserDataset(this.architecture);
            }
        }
    }

    public Comet_problem_architecture_insert_input getArchitectureSyncObject(){
        if(this.architecture_id == -1 || this.architecture_dataset_id == -1){
            System.out.println("--> ERROR, DESIGN NOT EVALUATED");
            return null;
        }

        // --> Build Objectives
        Comet_problem_objectivevalue_arr_rel_insert_input.Builder obj_build = Comet_problem_objectivevalue_arr_rel_insert_input.builder();
        Comet_problem_objectivevalue_arr_rel_insert_input db_objectives = obj_build.data(this.database_api.cloneArchObjectives(this.architecture)).build();

        Comet_problem_architecture_insert_input.Builder build = Comet_problem_architecture_insert_input.builder();
        return build.dataset_id(this.database_api.user_dataset_id)
                .problem_id(this.database_api.problem_id)
                .dataset_id(this.database_api.user_dataset_id)
                .evaluation_status(this.architecture.evaluation_status())
                .representation(this.architecture.representation())
                .origin(this.architecture.origin())
                .user_information_id(this.database_api.user_id)
                .comet_problem_objectivevalues(db_objectives)
                .build();
    }



    // ------------
    // --- MOEA ---
    // ------------

    public String generateRandomDesign(){
        StringBuilder builder = new StringBuilder();
        Random rand = new Random();
        List<ProblemDecisionsQuery.Item> problemDecisions = this.database_api.getProblemDecisions();
        for(int x = 0; x < problemDecisions.size(); x++){
            String decision_type = problemDecisions.get(x).type();
            int num_options = problemDecisions.get(x).alternatives().size();
            if(decision_type.equals("standard-form")){
                builder.append(rand.nextInt(num_options));
            }
            else if(decision_type.equals("down-selecting")){
                for(int y = 0; y < num_options; y++){
                    builder.append(rand.nextInt(2));
                }
            }
        }
        return builder.toString();
    }

    public void mutate(){
        ArrayList<Integer> chromosome = this.getChromosome();
        int num_bits = chromosome.size();

        List<ProblemDecisionsQuery.Item> problemDecisions = this.database_api.getProblemDecisions();
        int bit_index = 0;
        for(int x = 0; x < problemDecisions.size(); x++){
            String decision_type = problemDecisions.get(x).type();
            int num_options = problemDecisions.get(x).alternatives().size();
            if(decision_type.equals("standard-form")){
                if(this.getProbabilityResult(1.0/num_bits)){
                    int mutated_value = this.rand.nextInt(num_options);
                    chromosome.set(bit_index, mutated_value);
                }
                bit_index++;
            }
            else if(decision_type.equals("down-selecting")){
                for(int y = 0; y < num_options; y++){
                    if(this.getProbabilityResult(1.0/num_bits)){
                        int mutated_value = this.rand.nextInt(2);
                        chromosome.set(bit_index, mutated_value);
                    }
                    bit_index++;
                }
            }
        }

        StringBuilder representation = new StringBuilder();
        for(Integer bit: chromosome){
            representation.append(Integer.toString(bit));
        }
        this.representation = representation.toString();
    }

    public AdaptiveArchitecture evaluate(String eval_message){
        if(!this.alreadyEvaluated){
            this.sqs.sendMessage(SendMessageRequest.builder()
                    .queueUrl(this.eval_queue)
                    .messageBody("ga_message")
                    .messageAttributes(this.getEvalMessage())
                    .delaySeconds(0)
                    .build());
            this.subscribeToSelf();
        }
        return this;
    }



    // ---------------
    // --- HELPERS ---
    // ---------------

    public boolean getProbabilityResult(double probability){
        return (this.rand.nextDouble() <= probability);
    }

    public void parseObjectives(){
//        System.out.println("\n----------> DESIGN: "+ this.representation +" <----------");

        // --> 1. Get problem objectives
        List<ProblemObjectivesQuery.Item> items = this.database_api.getProblemObjectives();

        for(int x = 0; x < items.size(); x++) {
            ProblemObjectivesQuery.Item item = items.get(x);
            ArrayList<Double> bounds = this.parseBounds(item.bounds());
            int objective_id = Integer.parseInt(item.id().toString());
            double obj_value;
            if(!this.alreadyExisted || !this.alreadyEvaluated){
                if(item.optimization().equals("min")){
                    obj_value = bounds.get(1);
                }
                else if(item.optimization().equals("max")){
                    obj_value = bounds.get(0);
                }
                else{
                    obj_value = bounds.get(1);
                }
            }
            else{
                if(this.objective_map.containsKey(objective_id)){
                    if(item.optimization().equals("min")){
                        obj_value = this.objective_map.get(objective_id);
                    }
                    else if(item.optimization().equals("max")){
                        obj_value = (-1.0) * this.objective_map.get(objective_id);
                    }
                    else{
                        obj_value = (-1.0) * this.objective_map.get(objective_id);
                    }
                }
                else{
                    System.out.println("--> ERROR, ARCH OBJECTIVE NOT FOUND");
                    obj_value = bounds.get(1);
                }
            }
//            System.out.println("---> " + item.name() + ": " + obj_value);
            this.setObjective(x, obj_value);
        }
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

    public String getDesignJsonString(){
        return this.gson.toJson(this.architecture);
    }

    public ArrayList<Integer> getChromosome(){
        ArrayList<Integer> chromosome = new ArrayList<>();
        for(int x = 0; x < this.representation.length(); x++){
            chromosome.add(Integer.parseInt(String.valueOf(this.representation.charAt(x))));
        }
        return chromosome;
    }

    public Map<String, MessageAttributeValue> getEvalMessage() {
        final Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
        messageAttributes.put("msgType",
                MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue("evaluate")
                        .build()
        );
        messageAttributes.put("input",
                MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(this.representation)
                        .build()
        );
        messageAttributes.put("dataset_id",
                MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(String.valueOf(this.database_api.ga_dataset_id))
                        .build()
        );
        messageAttributes.put("origin",
                MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(this.database_api.ga_id)
                        .build()
        );
        return messageAttributes;
    }






    // -----------------
    // --- OVERRIDES ---
    // -----------------

    @Override
    public Solution copy() {
        return new AdaptiveArchitecture(this);
    }

    @Override
    public boolean equals(Object obj){
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        if(this.representation.equals(((AdaptiveArchitecture) obj).representation)){
            return true;
        }
        else{
            return false;
        }
    }

    @Override
    public int hashCode(){
        return this.representation.hashCode();
    }

    @Override
    public String toString(){
        return this.representation;
    }
}
