package algorithm.search.adaptive;

import database.DatabaseAPI;
import org.moeaframework.core.Solution;
import org.moeaframework.problem.AbstractProblem;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;


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

        AdaptiveArchitecture solution = (AdaptiveArchitecture) sltn;
        solution.evaluate("nfe" + this.nfe);
    }



    @Override
    public Solution newSolution() {
        return new AdaptiveArchitecture(this.databaseAPI);
    }
}
