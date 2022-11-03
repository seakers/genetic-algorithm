package database;



/*
    Each GA thread has a DatabaseAPI instance
 */


import com.algorithm.*;
import com.algorithm.type.Comet_problem_objectivevalue_insert_input;
import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.rx2.Rx2Apollo;
import com.apollographql.apollo.subscription.WebSocketSubscriptionTransport;
import io.reactivex.Observable;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DatabaseAPI {


    private ApolloClient apollo;
    private OkHttpClient http;
    private String apolloUrl = System.getenv("APOLLO_URL");  // http://graphql.comet:8080/v1/graphql
    private String apolloWsUrl = System.getenv("APOLLO_URL_WS");

    public int user_id;
    public int problem_id;
    public int user_dataset_id;
    public String ga_id;
    public List<Object> objective_ids;
    public int ga_dataset_id;

    public DatabaseAPI(int user_id, int problem_id, int user_dataset_id, String ga_id, List<Integer> objective_ids){
        this.user_id = user_id;
        this.problem_id = problem_id;
        this.user_dataset_id = user_dataset_id;
        this.ga_id = ga_id;

        this.objective_ids = new ArrayList<>();
        this.objective_ids.addAll(objective_ids);

        this.http = new OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .callTimeout(120, TimeUnit.SECONDS)
                .addInterceptor(new Interceptor() {
                    @NotNull
                    @Override
                    public okhttp3.Response intercept(@NotNull Interceptor.Chain chain) throws IOException {
                        Request original = chain.request();
                        Request request = original.newBuilder()
                                .header("X-Hasura-Admin-Secret", "comet")
                                .method(original.method(), original.body())
                                .build();

                        return chain.proceed(request);
                    }
                })
                .build();

        this.apollo = ApolloClient.builder()
                .serverUrl(this.apolloUrl)
                .subscriptionTransportFactory(new WebSocketSubscriptionTransport.Factory(this.apolloWsUrl, this.http)) // ws://graphql:8080/v1/graphql
                .okHttpClient(this.http)
                .build();

        this.ga_dataset_id = this.getGaDatasetId();
    }

    public int getNumObjectives(){
        return this.objective_ids.size();
    }

    public int getNumDecisions(){
        return this.getProblemDecisions().size();
    }

    public int getNumConstraints(){
        return 0;
    }


    public List<ProblemDecisionsQuery.Item> getProblemDecisions(){
        ProblemDecisionsQuery query = ProblemDecisionsQuery.builder()
                .problem_id(this.problem_id)
                .build();
        ApolloCall<ProblemDecisionsQuery.Data> apolloCall = this.apollo.query(query);
        Observable<Response<ProblemDecisionsQuery.Data>> observable  = Rx2Apollo.from(apolloCall);
        return observable.blockingFirst().getData().items();
    }

    public List<ProblemObjectivesQuery.Item> getProblemObjectives(){
        ProblemObjectivesQuery query = ProblemObjectivesQuery.builder()
                .problem_id(this.problem_id)
                .objective_ids(this.objective_ids)
                .build();
        ApolloCall<ProblemObjectivesQuery.Data> apolloCall = this.apollo.query(query);
        Observable<Response<ProblemObjectivesQuery.Data>> observable  = Rx2Apollo.from(apolloCall);
        return observable.blockingFirst().getData().items();
    }

    public List<InitialPopulationQuery.Item> getInitialPopulation(){
        InitialPopulationQuery query = InitialPopulationQuery.builder()
                .problem_id(this.problem_id)
                .dataset_id(this.user_dataset_id)
                .objective_ids(this.objective_ids)
                .build();
        ApolloCall<InitialPopulationQuery.Data> apolloCall = this.apollo.query(query);
        Observable<Response<InitialPopulationQuery.Data>> observable  = Rx2Apollo.from(apolloCall);
        return observable.blockingFirst().getData().items();
    }

    public List<SingleArchitectureQuery.Item> getArchitecture(String representation){
        SingleArchitectureQuery query = SingleArchitectureQuery.builder()
                .problem_id(this.problem_id)
                .objective_ids(this.objective_ids)
                .representation(representation)
                .build();
        ApolloCall<SingleArchitectureQuery.Data> apolloCall = this.apollo.query(query);
        Observable<Response<SingleArchitectureQuery.Data>> observable  = Rx2Apollo.from(apolloCall);
        return observable.blockingFirst().getData().items();
    }




    public int copyArchitectureToUserDataset(SingleArchitectureQuery.Item arch){
        if(this.getArchitecture(arch.representation()).isEmpty()){
            return -1;
        }

        // --> Clone Objective Objects for insert
        ArrayList<Comet_problem_objectivevalue_insert_input> objectives = this.cloneArchObjectives(arch);

        CopyArchitectureMutation mutation = CopyArchitectureMutation.builder()
                .problem_id(this.problem_id)
                .user_dataset_id(this.user_dataset_id)
                .evaluation_status(arch.evaluation_status())
                .representation(arch.representation())
                .origin(arch.origin())
                .user_info_id(this.user_id)
                .objective_values(objectives)
                .build();
        ApolloCall<CopyArchitectureMutation.Data> apolloCall = this.apollo.mutate(mutation);
        Observable<Response<CopyArchitectureMutation.Data>> observable  = Rx2Apollo.from(apolloCall);
        return ((BigDecimal) observable.blockingFirst().getData().items().id()).intValue();
    }

    private ArrayList<Comet_problem_objectivevalue_insert_input> cloneArchObjectives(SingleArchitectureQuery.Item arch){
        ArrayList<Comet_problem_objectivevalue_insert_input> new_objectives = new ArrayList<>();
        for(SingleArchitectureQuery.Objective objective: arch.objective()){
            Comet_problem_objectivevalue_insert_input new_objective = Comet_problem_objectivevalue_insert_input.builder()
                    .explanation(objective.explanation())
                    .objective_id(objective.objective_id())
                    .value(objective.value())
                    .build();
            new_objectives.add(new_objective);
        }
        return new_objectives;
    }


    public int getGaDatasetId(){
        GaDatasetQuery query = GaDatasetQuery.builder()
                .problem_id(this.problem_id)
                .build();
        ApolloCall<GaDatasetQuery.Data> apolloCall = this.apollo.query(query);
        Observable<Response<GaDatasetQuery.Data>> observable  = Rx2Apollo.from(apolloCall);
        BigDecimal id = (BigDecimal) observable.blockingFirst().getData().items().get(0).id();
        return id.intValue();
    }












}
