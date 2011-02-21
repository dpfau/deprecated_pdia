/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.columbia.neuro.pfau.pdia;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

/**
 *
 * @author davidpfau
 */
public class PDIA2 implements Cloneable {

    private HashMap<Integer,Integer>[] delta;
    private double alpha;
    private double alpha0;
    private double beta;
    private int numSymbols;

    private ArrayList<Object> alphabet;
    private ArrayList<ArrayList<Integer>> trainingData;
    private ArrayList<ArrayList<Integer>> testingData;

    private ArrayList<Restaurant<Integer,Integer>> restaurants; // Maps a symbol in the alphabet to the corresponding restaurant
    private Restaurant<Table<Integer>,Integer> top;

    public PDIA2(int nsym) {
        numSymbols = nsym;
        delta = new HashMap[nsym];
        for (int i = 0; i < nsym; i++) {
            delta[i] = new HashMap<Integer,Integer>();
        }
        alpha = 1.0;
        alpha0 = 1.0;
        beta = 1.0;
        alphabet = new ArrayList<Object>();
        top = new Restaurant<Table<Integer>,Integer>(alpha0,0,new Geometric(0.001));

        trainingData = new ArrayList<ArrayList<Integer>>();
        testingData = new ArrayList<ArrayList<Integer>>();
        restaurants = new ArrayList<Restaurant<Integer,Integer>>();
    }

    public PDIA2(ArrayList<ArrayList<Object>> data, int nTrain, int nsym) {
        numSymbols = nsym;
        delta = new HashMap[nsym];
        for (int i = 0; i < nsym; i++) {
            delta[i] = new HashMap<Integer,Integer>();
        }        alpha = 1.0;
        alpha0 = 1.0;
        beta = 1.0;
        alphabet = new ArrayList<Object>();
        top = new Restaurant<Table<Integer>,Integer>(alpha0,0,new Geometric(0.001));

        trainingData = new ArrayList<ArrayList<Integer>>();
        testingData = new ArrayList<ArrayList<Integer>>();
        restaurants = new ArrayList<Restaurant<Integer,Integer>>();
        for (int i = 0; i < data.size(); i++) {
            int state = 0;
            ArrayList<Integer> line = new ArrayList<Integer>();
            if (i < nTrain) {
                for (int j = 0; j < data.get(i).size(); j++) {
                    if (alphabet.contains(data.get(i).get(j))) {
                        line.add(alphabet.indexOf(data.get(i).get(j)));
                        state = next(state,alphabet.indexOf(data.get(i).get(j)));
                    } else {
                        alphabet.add(data.get(i).get(j));
                        restaurants.add(new Restaurant<Integer,Integer>(alpha,0,top));
                        line.add(alphabet.size()-1);
                        state = next(state,alphabet.size()-1);
                    }
                }
                trainingData.add(line);
            } else {
                for (int j = 0; j < data.get(i).size(); j++) {
                    line.add(alphabet.indexOf(data.get(i).get(j)));
                }
                testingData.add(line);
            }
        }
        assert numSymbols == alphabet.size() : "Incorrect alphabet size!";
    }

    public int next(int state, int symbol) {
        Integer nxt = delta[symbol].get(state);
        if (nxt == null) {
            Restaurant r = restaurants.get(symbol);
            Integer dish = (Integer)r.seat(state);
            delta[symbol].put(state, dish);
            return dish;
        } else {
            return nxt;
        }
    }

    public double dataLogLikelihood(HashMap<Pair,Integer> counts) {
        HashMap<Integer,Integer> stateCounts = stateCount(counts);
        double logLike = 0.0;
        for (Integer i : counts.values()) {
            logLike += Gamma.logGamma(i + beta/numSymbols) - Gamma.logGamma(beta/numSymbols);
        }
        for (Integer j : stateCounts.values()) {
            logLike -= Gamma.logGamma(j + beta) - Gamma.logGamma(beta);
        }
        return logLike;
    }

    public double trainingLogLikelihood() {
        return dataLogLikelihood(count(trainingData));
    }

    public double testingLogLikelihood() {
        return dataLogLikelihood(count(testingData));
    }

    public int trainLen() {
        int n = 0;
        for (ArrayList a : trainingData) {
            n += a.size();
        }
        return n;
    }

    public int testLen() {
        int n = 0;
        for (ArrayList a : testingData) {
            n += a.size();
        }
        return n;
    }

    public HashMap<Integer,Integer>[] count(ArrayList<ArrayList<Integer>> data) {
        HashMap<Integer,Integer>[] counts = new HashMap[numSymbols];
        for (int i = 0; i < numSymbols; i++) {
            counts[i] = new HashMap<Integer,Integer>();
        }
        for (int i = 0; i < data.size(); i++) {
            int state = 0;
            for (int j = 0; j < data.get(i).size(); j ++) {
                int datum = data.get(i).get(j);
                Integer ct = counts[datum].get(state);
                if (ct == null) {
                    counts[datum].put(state, 1);
                } else {
                    counts[datum].put(state, ct + 1);
                }
                state = next(state, data.get(i).get(j));
            }
        }
        return counts;
    }

    public HashMap<Integer,Integer>[] trainCount() { // Hacky, but leads to important speedup because we don't need to repeat calls to count
        return count(trainingData);
    }

    public HashMap<Integer,Integer>[] testCount() {
        return count(testingData);
    }

    public HashMap<Integer,Integer> stateCount(HashMap<Integer,Integer>[] counts) {
        HashMap<Integer,Integer> stateCounts = new HashMap<Integer,Integer>();
        for (int i = 0; i < counts.length; i++) {
            for (int state : counts[i].keySet()) {
                Integer ct = stateCounts.get(state);
                if (ct == null) {
                    stateCounts.put(state, counts[i].get(state));
                } else {
                    stateCounts.put(state, ct + counts[i].get(state));
                }
            }
        }
        return stateCounts;
    }

    public int numPairs() {
        return delta.size();
    }

    //number of states, counting the zero state
    public int numStates() {
        return top.dishes() + 1;
    }

    public static PDIA2 sample(PDIA2 p1) {
        Set<Pair> pairs = p1.delta.keySet();
        HashMap<Pair,Integer> cts1 = p1.trainCount();
        for (Pair p : pairs) {
            PDIA2 p2 = p1.clone();
            p2.clear(p);
            HashMap<Pair,Integer> cts2 = p2.trainCount();
            if ( p2.dataLogLikelihood(cts2) - p1.dataLogLikelihood(cts1) > Math.log(Math.random()) ) {
                ArrayList<Pair> empty = new ArrayList<Pair>();
                for (Pair q : p2.delta.keySet()) {
                    if (!cts2.containsKey(q)) { // If the count for that state/symbol pair is zero
                        empty.add(q);
                    }
                }
                for (Pair q : empty) { // Avoids concurrent modification problems
                    p2.clear(q);
                }
                p1 = p2;
                cts1 = cts2;
                System.gc();
            }
        }
        return p1;
    }

    @Override
    public PDIA2 clone() {
        PDIA2 p = new PDIA2();
        p.alpha     = this.alpha;
        p.alpha0    = this.alpha0;
        p.beta      = this.beta;
        p.alphabet  = this.alphabet;
        p.numSymbols = this.numSymbols;

        p.trainingData = this.trainingData;
        p.testingData  = this.testingData; // cloning ought not to matter since this isn't really mutable

        p.top = this.top.clone();
        HashMap<Table<Integer>,Table<Integer>> tableMap = p.top.cloneCustomers();

        p.restaurants = new ArrayList<Restaurant<Integer,Integer>>();
        for (Restaurant r : this.restaurants) {
            Restaurant<Integer,Integer> s = (Restaurant<Integer,Integer>)r.clone();
            s.swapTables(tableMap);
            s.setBaseDistribution(p.top);
            p.restaurants.add(s);
        }

        p.delta = (HashMap<Pair,Integer>)delta.clone();

        return p;
    }

    public void clear() {
        for (Pair p : delta.keySet()) {
            boolean b = restaurants.get(p.symbol()).unseat(p.state());
            assert b : "Cleared customer that wasn't in the restaurant!";
        }
        delta.clear();
    }

    public void clear(Pair p) {
        restaurants.get(p.symbol()).unseat(p.state());
        delta.remove(p);
    }

    private class Pair {
        private int state;
        private int symbol;

        public Pair(int a, int b) {
            this.state = a;
            this.symbol = b;
        }

        public int state() { return state; }
        public int symbol() { return symbol; }

        @Override
        public boolean equals(Object o) {
            if ( o == null ) {
                return false;
            } else if ( o.getClass() != this.getClass() ) {
                return false;
            } else {
                return this.state == ((Pair)o).state() && this.symbol == ((Pair)o).symbol();
            }
        }

        @Override
        public int hashCode() {
            return 103*state + 107*symbol;
        }
    }
}
