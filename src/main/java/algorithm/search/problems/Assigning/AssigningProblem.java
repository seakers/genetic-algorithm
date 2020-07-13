/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package algorithm.search.problems.Assigning;

import com.algorithm.ArchitectureSubscriptionQuery;
import com.algorithm.SingleArchitectureQuery;
import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.rx2.Rx2Apollo;
import io.reactivex.Observable;
import org.moeaframework.core.Solution;
import org.moeaframework.core.variable.BinaryVariable;
import org.moeaframework.problem.AbstractProblem;
import seakers.architecture.problem.SystemArchitectureProblem;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


/**
 * An assigning problem to optimize the allocation of n instruments to m orbits.
 * Also can choose the number of satellites per orbital plane. Objectives are
 * cost and scientific benefit
 *
 * @author nozomihitomi
 */
public class AssigningProblem extends AbstractProblem implements SystemArchitectureProblem {

    private final int[] alternativesForNumberOfSatellites;

    private final SqsClient sqs;

    private final ApolloClient apollo;

    private final String queueUrl;

    private final int problem_id;

    private final double dcThreshold = 0.5;

    private final double massThreshold = 3000.0; //[kg]

    private final double packingEffThreshold = 0.4; //[kg]

    private final int numInstruments;

    private final int numOrbits;

    private final int delay = 0;

    private final int timeout = 10000;

    /**
     * @param alternativesForNumberOfSatellites
     */
    public AssigningProblem(SqsClient sqs, ApolloClient apollo, int[] alternativesForNumberOfSatellites, int numOrbits, int numInstruments, String queueUrl, int problem_id) {
        //2 decisions for Choosing and Assigning Patterns
        super(1 + numInstruments * numOrbits, 2);
        this.numInstruments = numInstruments;
        this.numOrbits = numOrbits;
        this.sqs = sqs;
        this.queueUrl = queueUrl;
        this.alternativesForNumberOfSatellites = alternativesForNumberOfSatellites;
        this.problem_id = problem_id;
        this.apollo = apollo;
    }

    @Override
    public void evaluate(Solution sltn) {
        AssigningArchitecture arch = (AssigningArchitecture) sltn;

        if (!arch.getAlreadyEvaluated()){
            evaluateArch(arch);
        }
        else{
            System.out.println("---> Architecture already evaluated!!!");
        }

        System.out.println(String.format("Arch %s Science = %10f; Cost = %10f", arch.toString(), arch.getObjective(0), arch.getObjective(1)));
    }

    public String toNumeralString(final Boolean input) {
        if (input == null) {
            return "null";
        } else {
            return input.booleanValue() ? "1" : "0";
        }
    }

    private void evaluateArch(AssigningArchitecture arch){
        String input = "";

        for(int i = 1; i < arch.getNumberOfVariables(); i++){
            BinaryVariable var = (BinaryVariable)arch.getVariable(i);
            boolean binaryVal = var.get(0);
            input += toNumeralString(binaryVal);
        }

        System.out.println("---> EVALUATING ARCHITECTURE: " + input);



        // Check to see if that architecture already exists



        // Send message to vassar
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
                        .stringValue(input)
                        .build()
        );
        messageAttributes.put("ga",
                MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue("true")
                        .build()
        );
        System.out.println("---> Processing architecure");
        this.sqs.sendMessage(SendMessageRequest.builder()
                                            .queueUrl(this.queueUrl)
                                            .messageBody("")
                                            .messageAttributes(messageAttributes)
                                            .delaySeconds(this.delay)
                                            .build());

        // Now wait for response
        try{
            while(!this.runningStatusCheck(input)){
                System.out.println("---> processing...");
                TimeUnit.SECONDS.sleep(2);
            }
        }
        catch(Exception e){
            System.out.println("---> Error evaluating architecture!!!!");
        }

        System.out.println("---> Architecture has finished!!!");

        SingleArchitectureQuery.Item result = this.getArchitecture(input);

        double science = Double.parseDouble(result.science().toString());
        double cost    = Double.parseDouble(result.cost().toString());

        // Add results to arch!!!
        arch.setObjective(0, -science);
        arch.setObjective(1, cost);
        arch.setAlreadyEvaluated(true);
    }


    public boolean runningStatusCheck(String input){
        ArchitectureSubscriptionQuery subQuery = ArchitectureSubscriptionQuery.builder()
                .problem_id(problem_id)
                .input(input)
                .build();
        ApolloCall<ArchitectureSubscriptionQuery.Data> apolloCall  = this.apollo.query(subQuery);
        Observable<Response<ArchitectureSubscriptionQuery.Data>> observable  = Rx2Apollo.from(apolloCall);
        return (observable.blockingFirst().getData().items().aggregate().count() > 0);
    }

    public SingleArchitectureQuery.Item getArchitecture(String input){
        SingleArchitectureQuery archQuery = SingleArchitectureQuery.builder()
                                                                    .problem_id(problem_id)
                                                                    .input(input)
                                                                    .build();
        ApolloCall<SingleArchitectureQuery.Data> apolloCall  = this.apollo.query(archQuery);
        Observable<Response<SingleArchitectureQuery.Data>> observable  = Rx2Apollo.from(apolloCall);
        return observable.blockingFirst().getData().items().get(0);
    }


    @Override
    public Solution newSolution() {
        return new AssigningArchitecture(alternativesForNumberOfSatellites, this.numInstruments, this.numOrbits, 2);
    }

}
