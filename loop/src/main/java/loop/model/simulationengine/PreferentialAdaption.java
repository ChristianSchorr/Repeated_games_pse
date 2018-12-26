package loop.model.simulationengine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import loop.model.simulationengine.distributions.UniformFiniteDistribution;
import loop.model.simulationengine.strategies.MixedStrategy;
import loop.model.simulationengine.strategies.Strategy;

/**
 * Realises the "preferential adaption" adaption mechanism described in the specification.
 * 
 * @author Peter Koepernik
 *
 */
public class PreferentialAdaption implements StrategyAdjuster {
    
    private double alpha;
    private double beta;
    
    /**
     * Creates a new preferential adaption instance with the given parameters.
     * 
     * @param alpha the comparing probability
     * @param beta the adjusting probability
     */
    public PreferentialAdaption(double alpha, double beta) {
        if (alpha < 0 || alpha > 1 || beta < 0 || beta > 1) {
            throw new IllegalArgumentException("Invalid parameters for initialisation of replicator dynamic.");
        }
        
        this.alpha = alpha;
        this.beta = beta;
    }
    
    @Override
    public void adaptStrategies(List<Agent> agents, SimulationHistory history) {
        double beta_prime = this.beta / (agents.size() - 1);
        
        //check whether all strategies are mixed
        boolean allMixed = true;
        for (Agent agent: agents) {
            if (!(agent.getStrategy() instanceof MixedStrategy)) allMixed = false;
        }
        
        //UniformFiniteDistribution<Agent> dist = new UniformFiniteDistribution<Agent>(agents);
        for (Agent agentA: agents) {
            Random r = new Random();
            if (r.nextDouble() > this.alpha) continue;
            
            //choose Agent B
            Agent agentB = null;
            double P = 0; //P = sum_{l=/=i} p_{il}
            for (Agent a: agents) {
                if (a == agentA) continue;
                P += agentA.getStrategy().getCooperationProbability(agentA, a, history);
            }
            double p = P * r.nextDouble();
            for (Agent a: agents) {
                if (a == agentA) continue;
                if (p <= agentA.getStrategy().getCooperationProbability(agentA, a, history)) {
                    agentB = a;
                    break;
                }
                p -= agentA.getStrategy().getCooperationProbability(agentA, a, history);
            }
            
            //assert agentB != null, sonst probabilities fucked up
            
            int deltaR = agents.indexOf(agentA) - agents.indexOf(agentB);
            if (deltaR < 0) continue;
            
            //adapt strategy
            double delta = beta_prime * deltaR * agentA.getStrategy().getCooperationProbability(agentA, agentB, history);
            if (allMixed) {
                MixedStrategy stratA = (MixedStrategy) agentA.getStrategy();
                MixedStrategy stratB = (MixedStrategy) agentB.getStrategy();
                List<Strategy> partStrats = stratB.getComponentStrategies();
                for (Strategy s: stratA.getComponentStrategies()) {
                    if (!partStrats.contains(s))
                        partStrats.add(s);
                }
                
                List<Double> interpolationProbs = new ArrayList<Double>();
                for (Strategy s: partStrats) {
                    double probA = (stratA.getComponentStrategies().contains(s)) ? stratA.getComponent(stratA.getComponentStrategies().indexOf(s)) : 0.0;
                    double probB = (stratB.getComponentStrategies().contains(s)) ? stratB.getComponent(stratB.getComponentStrategies().indexOf(s)) : 0.0;
                    interpolationProbs.add(probA + delta * (probB - probA));
                }
                agentA.setStrategy(new MixedStrategy("--autogenerated--", "--autogenerated--", partStrats, interpolationProbs));
            } else {
                if (r.nextDouble() <= delta)
                    agentA.setStrategy(agentB.getStrategy());
            }
        }
    }

}
