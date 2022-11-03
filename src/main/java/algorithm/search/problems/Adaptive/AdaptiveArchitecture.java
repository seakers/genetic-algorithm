package algorithm.search.problems.Adaptive;

import com.algorithm.ProblemDecisionsQuery;
import com.algorithm.SingleArchitectureQuery;
import com.google.gson.Gson;
import database.DatabaseAPI;
import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.BinaryIntegerVariable;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class AdaptiveArchitecture extends Solution {


    public SingleArchitectureQuery.Item architecture = null;
    public Gson gson = new Gson();
    public boolean alreadyEvaluated;
    public boolean alreadyExisted;

    public String representation;

    public DatabaseAPI database_api;

    public Integer architecture_id = -1;
    public Integer architecture_dataset_id = -1;
    public Random rand = new Random();

    public HashMap<Integer, Double> objective_map = new HashMap<>();

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
    }

    public AdaptiveArchitecture(DatabaseAPI database_api, String chromosome){
        super(database_api.getNumDecisions(), database_api.getNumObjectives(), database_api.getNumConstraints());
        this.database_api = database_api;
        this.representation = chromosome;
        this.copy_architecture_db_info();
        this.setInnerVariables();
    }

    public AdaptiveArchitecture(DatabaseAPI database_api){
        super(database_api.getNumDecisions(), database_api.getNumObjectives(), database_api.getNumConstraints());
        this.database_api = database_api;
        this.representation = this.generateRandomDesign();
        this.copy_architecture_db_info();
        this.setInnerVariables();
    }

    public void setInnerVariables(){
        List<ProblemDecisionsQuery.Item> problemDecisions = this.database_api.getProblemDecisions();
        int counter = 0;
        for(int x = 0; x < problemDecisions.size(); x++){
            int num_options = problemDecisions.get(x).alternatives().size();
            BinaryIntegerVariable var = new BinaryIntegerVariable(0, 0, num_options);
            this.setVariable(counter, var);
            counter++;
        }
    }

    // --> Subscribes to arch db entry in any dataset until arch is indexed by evaluator or timeout
    public boolean subscribeToSelf(){
        int timeout = 200;
        int counter = 0;
        try{
            this.copy_architecture_db_info();
            while(!this.alreadyExisted && counter < timeout){
                System.out.println("---> " + this.database_api.ga_id + " processing...");
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

    // --> Queries arch db entry in any dataset, copy
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
        }
    }


    public void syncDesignWithUserDataset(){
        if(this.architecture_id == -1 || this.architecture_dataset_id == -1){
            System.out.println("--> ERROR, DESIGN NOT EVALUATED");
            return;
        }
        System.out.println("--> COPYING ARCH FROM DATASET TO: " + this.architecture_dataset_id + " " + this.database_api.user_dataset_id);

        if(this.architecture_dataset_id != this.database_api.user_dataset_id){
            int arch_id = this.database_api.copyArchitectureToUserDataset(this.architecture);
            System.out.println("--> NEW ARCH ID: " + arch_id);
        }
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

    public boolean getProbabilityResult(double probability){
        return (this.rand.nextDouble() <= probability);
    }





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







    public boolean isAlreadyEvaluated(){
        return this.alreadyEvaluated;
    }

    public void setAlreadyEvaluated(boolean alreadyEvaluated) {
        this.alreadyEvaluated = alreadyEvaluated;
    }

    public boolean getAlreadyEvaluated() {
        return this.alreadyEvaluated;
    }

    public void setAlreadyExisted(boolean alreadyExisted) {
        this.alreadyExisted = alreadyExisted;
    }

    public boolean getAlreadyExisted() {
        return this.alreadyExisted;
    }






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
