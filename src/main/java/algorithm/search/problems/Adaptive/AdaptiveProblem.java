package algorithm.search.problems.Adaptive;

import com.algorithm.ProblemObjectivesQuery;
import com.algorithm.SingleArchitectureQuery;
import database.DatabaseAPI;
import org.moeaframework.core.Solution;
import org.moeaframework.problem.AbstractProblem;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AdaptiveProblem extends AbstractProblem {

    public DatabaseAPI databaseAPI;
    public SqsClient sqs;

    public int nfe = 0;

    public String eval_queue = System.getenv("EVAL_REQUEST_URL");

    public AdaptiveProblem(DatabaseAPI database_api) {
        super(database_api.getNumDecisions(), database_api.getNumObjectives());
        this.databaseAPI = database_api;
        this.sqs = SqsClient.builder().region(Region.US_EAST_2).build();
    }



    @Override
    public void evaluate(Solution sltn) {
        this.nfe++;

        AdaptiveArchitecture arch = (AdaptiveArchitecture) sltn;
        String input = arch.representation;
        if(arch.alreadyEvaluated){
            this.parseObjectives(arch);
            return;
        }

        this.sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(this.eval_queue)
                .messageBody("ga_message")
                .messageAttributes(arch.getEvalMessage())
                .delaySeconds(0)
                .build());

        boolean result = arch.subscribeToSelf();

        // Parse objectives
        this.parseObjectives(arch);
    }


    public void parseObjectives(AdaptiveArchitecture arch){
        System.out.println("\n----------> DESIGN "+this.nfe+": "+ arch.representation +" <----------");

        // --> 1. Get problem objectives
        List<ProblemObjectivesQuery.Item> items = this.databaseAPI.getProblemObjectives();

        for(int x = 0; x < items.size(); x++) {
            ProblemObjectivesQuery.Item item = items.get(x);
            ArrayList<Double> bounds = this.parseBounds(item.bounds());
            int objective_id = Integer.parseInt(item.id().toString());
            double obj_value;
            if(!arch.alreadyExisted || !arch.alreadyEvaluated){
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
                if(arch.objective_map.containsKey(objective_id)){
                    if(item.optimization().equals("min")){
                        obj_value = arch.objective_map.get(objective_id);
                    }
                    else if(item.optimization().equals("max")){
                        obj_value = (-1.0) * arch.objective_map.get(objective_id);
                    }
                    else{
                        obj_value = (-1.0) * arch.objective_map.get(objective_id);
                    }
                }
                else{
                    System.out.println("--> ERROR, ARCH OBJECTIVE NOT FOUND");
                    obj_value = bounds.get(1);
                }
            }
            System.out.println("---> " + item.name() + ": " + obj_value);
            arch.setObjective(x, obj_value);
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




    @Override
    public Solution newSolution() {
        return new AdaptiveArchitecture(this.databaseAPI);
    }
}
