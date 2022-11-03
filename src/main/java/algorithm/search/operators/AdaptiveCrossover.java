package algorithm.search.operators;

import algorithm.search.problems.Adaptive.AdaptiveArchitecture;
import database.DatabaseAPI;
import org.moeaframework.core.Solution;
import org.moeaframework.core.Variation;

import java.util.ArrayList;
import java.util.Random;

public class AdaptiveCrossover implements Variation {


    public DatabaseAPI databaseAPI;


    public AdaptiveCrossover(DatabaseAPI databaseAPI){
        this.databaseAPI = databaseAPI;

    }

    @Override
    public Solution[] evolve(Solution[] parents){

        Random rand = new Random();

        // TWO PARENTS FOR CROSSOVER
        Solution result1 = parents[0].copy();
        Solution result2 = parents[1].copy();

        // CAST APPROPRIATELY
        AdaptiveArchitecture res1 = (AdaptiveArchitecture) result1;
        AdaptiveArchitecture res2 = (AdaptiveArchitecture) result2;

        ArrayList<Integer> papa = res1.getChromosome();
        ArrayList<Integer> mama = res2.getChromosome();
        ArrayList<Integer> child = new ArrayList<>();

        // CROSSOVER
        for(int idx = 0; idx < papa.size(); idx++){
            if(rand.nextBoolean()){
                child.add(papa.get(idx));
            }
            else{
                child.add(mama.get(idx));
            }
        }

        // CONVERT CHILD + CREATE
        StringBuilder child_representation = new StringBuilder();
        for(Integer bit: child){
            child_representation.append(Integer.toString(bit));
        }
        AdaptiveArchitecture child_design = new AdaptiveArchitecture(this.databaseAPI, child_representation.toString());
        child_design.mutate();

        // RETURN CHILD
        Solution[] soln = new Solution[] { child_design };
        return soln;
    }


    @Override
    public int getArity(){
        return this.databaseAPI.getNumObjectives();
    }

}
