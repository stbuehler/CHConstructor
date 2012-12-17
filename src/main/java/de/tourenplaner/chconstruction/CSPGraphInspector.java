/*
 * (C) Copyright 2012 Peter Vollmer
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2
 * of the License.
 */

package de.tourenplaner.chconstruction;

import sun.org.mozilla.javascript.tools.shell.Global;

import javax.print.PrintServiceLookup;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;
import java.util.concurrent.DelayQueue;

/**
 * User: Peter Vollmer
 * Date: 11/9/12
 * Time: 1:25 PM
 */
public class CSPGraphInspector {
    RAMGraph chGraph;
    CSPDijkstra cspDijkstra;
    CSPCHDijkstra cspCHDijkstra;

    int costCSP;
    int resourceCSP;
    int costCSPCH;
    int resourceCSPCH;


    CSPGraphInspector (RAMGraph graph){
        this.chGraph = graph;
        cspDijkstra = new CSPDijkstra(graph);
        cspCHDijkstra = new CSPCHDijkstra(graph);
    }

    void inspectGraph (){
        Random random = new Random();
        random.setSeed(123456789);
        int src;
        int trg;
        int lambda;
        int maxLambda = 4096;
        int counter = 0;
        int cspDist;
        int[] cspPath;

        int cspCHDist;
        int[] cspCHPath;
        System.out.println("Nummer;Source;Target;Lambda;CSP;CSPCH;CSPBacktrack;CSPCHBacktrack");
        long cspStartTime,cspEndTime,cspBackTrackStartTime,cspBackTrackEndTime;
        long cspCHStartTime,cspCHEndTime,cspCHBackTrackStartTime,cspCHBackTrackEndTime;
        while (counter < 10000){

            counter++;
            System.err.println("Counter: "+counter);
            src = random.nextInt(chGraph.nofNodes());
            trg = random.nextInt(chGraph.nofNodes());
            lambda = random.nextInt(maxLambda);

            System.err.println("CSPGraphInspector: src="+src+", trg="+trg+", lambda="+lambda);

            cspStartTime=System.nanoTime();
            cspDist = cspDijkstra.runDijkstraWithoutSC(src, trg, lambda, maxLambda);
            cspEndTime=System.nanoTime();
            cspCHStartTime=System.nanoTime();
            cspCHDist = cspCHDijkstra.runDijkstra(src,trg,lambda,maxLambda);
            cspCHEndTime = System.nanoTime();
            if (cspDist == Integer.MAX_VALUE){
                if (cspCHDist == Integer.MAX_VALUE){
                    continue;
                } else {
                    System.err.println("csp finds no path from src("+src+") to trg("+trg+") and equals not to cspCH which found a path with dist("+cspCHDist+").");
                    return;
                }
            }
            if (cspCHDist == Integer.MAX_VALUE){
                System.err.println("cspCH finds no path from src("+src+") to trg("+trg+") and equals not to csp which found a path with dist("+cspDist+").");
                return;
            }
            cspBackTrackStartTime = System.nanoTime();
            cspPath = cspBacktrack(src,trg);
            cspBackTrackEndTime = System.nanoTime();
            cspCHBackTrackStartTime = System.nanoTime();
            cspCHPath = cspCHBacktrack(src,trg);
            cspCHBackTrackEndTime = System.nanoTime();
            System.out.println(counter+";"+src+";"+trg+";"+lambda+";"+(cspEndTime-cspStartTime)+";"+(cspCHEndTime-cspCHStartTime)+";"+(cspBackTrackEndTime-cspBackTrackStartTime)+";"+(cspCHBackTrackEndTime-cspCHBackTrackStartTime));

            System.err.println("Distcsp= "+cspDist+" Distcspch="+cspCHDist);
            if (cspDist != cspCHDist){
                System.err.println("cspDist("+cspDist+") equals not to cspCHDist("+cspCHDist+").");
                System.out.println("cspDist("+cspDist+") equals not to cspCHDist("+cspCHDist+").");

                printPathToGPX(cspPath,trg);


                printPathToGPX(cspCHPath,trg);
                return;
            }
        }
    }

    void inspectGraphCSP (){
        Random random = new Random();
        random.setSeed(123456789);
        int src;
        int trg;
        int lambda;
        int maxLambda = 4096;
        int lambdaOfGood;
        int lambdaOfBad;
        int resourceBound;
        int counter = 0;
        int cspDist;



        //ausgabe Variablen
        int costUpperBound;
        int costLowerBound;
        int iterations;
        int touchedNodes;
        int touchedEdges;
        System.out.println("Typ;Nummer;src;trg;ResBound;resource;Time;Iterations;touchedNodes;touchedEdges;AproxGüte");
        long cspStartTime,cspEndTime;
        //anzahl der queries
        while (counter < 1000){

            iterations = 0;
            touchedNodes = 0;
            touchedEdges = 0;
            counter++;
            System.err.println("Counter: "+counter);

            src = random.nextInt(chGraph.nofNodes());
            trg = random.nextInt(chGraph.nofNodes());
            //resourceBound = random.nextInt(5000);
            cspDist =  cspDijkstra.runDijkstra(src,trg,0,maxLambda);
            if (cspDist == Integer.MAX_VALUE){
                //kein weg gefunden
                continue;
            }
            backtrackCostResourceCSP(src,trg);
            resourceBound = (int)(resourceCSP * 1.1);

            //CSP
            cspStartTime = System.nanoTime();
            lambda = maxLambda;

            cspDist = cspDijkstra.runDijkstraWithoutSC(src, trg, lambda, maxLambda);

            backtrackCostResourceCSP(src,trg);
            costUpperBound = costCSP;
            touchedNodes += cspDijkstra.nofTouchedNodes;
            touchedEdges += cspDijkstra.edgeCount;
            iterations++;
            if(resourceCSP > resourceBound){
                //keinen schnellsten weg mit dem entsprechenden resourcebound gefunden
                lambdaOfBad = lambda;
                lambda = 0;
                cspDist = cspDijkstra.runDijkstraWithoutSC(src, trg, lambda, maxLambda);
                backtrackCostResourceCSP(src,trg);
                costLowerBound = costCSP;
                touchedNodes += cspDijkstra.nofTouchedNodes;
                touchedEdges += cspDijkstra.edgeCount;
                iterations++;
                if (resourceCSP > resourceBound){
                    System.err.println("gar keinen weg unter dem resourceBound gefunden");
                    continue;
                }
                lambdaOfGood = lambda;
                lambda = (lambdaOfGood + lambdaOfBad)/2;
                cspDist = cspDijkstra.runDijkstraWithoutSC(src, trg, lambda, maxLambda);
                backtrackCostResourceCSP(src,trg);
                touchedNodes += cspDijkstra.nofTouchedNodes;
                touchedEdges += cspDijkstra.edgeCount;
                iterations++;
                while (lambdaOfBad - lambdaOfGood >= 1 ) {
                    if (resourceCSP <= resourceBound) {
                        lambdaOfGood = lambda;
                        costLowerBound = costCSP;
                    } else {
                        lambdaOfBad = lambda;
                        costUpperBound = costCSP;
                    }
                    if (lambdaOfBad - lambdaOfGood == 1){
                        break;
                    }
                    lambda = (lambdaOfGood + lambdaOfBad) / 2;
                    cspDist = cspDijkstra.runDijkstraWithoutSC(src, trg, lambda, maxLambda);
                    backtrackCostResourceCSP(src,trg);
                    touchedNodes += cspDijkstra.nofTouchedNodes;
                    touchedEdges += cspDijkstra.edgeCount;
                    iterations++;
                }
                cspDijkstra.runDijkstraWithoutSC(src, trg, lambda, maxLambda);
                backtrackCostResourceCSP(src,trg);

            } else {
                costLowerBound = costUpperBound;
            }
            cspEndTime = System.nanoTime();
            System.out.println("csp;"+counter+";"+src+";"+trg+";"+resourceBound+";"+resourceCSP+";"+(cspEndTime-cspStartTime)+";"+iterations+";"+touchedNodes+";"+touchedEdges+";"+((float)costLowerBound/(float)costUpperBound));

        }
    }

    void inspectGraphCSPCH (){
        Random random = new Random();
        random.setSeed(123456789);
        int src;
        int trg;
        int lambda;
        int maxLambda = 4096;
        int lambdaOfGood;
        int lambdaOfBad;
        int resourceBound;
        int counter = 0;
        int cspDist;

        //ausgabe Variablen
        int costUpperBound;
        int costLowerBound;
        int iterations;
        int touchedNodes;
        int touchedEdges;
        System.out.println("Typ;Nummer;src;trg;ResBound;resource;Time;Iterations;touchedNodes;touchedEdges;AproxGüte");
        long cspStartTime,cspEndTime;
        //anzahl der queries
        while (counter < 1000){

            iterations = 0;
            touchedNodes = 0;
            touchedEdges = 0;
            counter++;
            System.err.println("Counter: "+counter);

            src = random.nextInt(chGraph.nofNodes());
            trg = random.nextInt(chGraph.nofNodes());
            cspCHDijkstra.runDijkstra(src,trg,0,maxLambda);
            backtrackCostResourceCSPCH(src,trg);
            resourceBound = (int)(resourceCSPCH * 1.1);
            //resourceBound = random.nextInt(5000);
            //CSP
            cspStartTime = System.nanoTime();
            lambda = maxLambda;

            cspDist = cspCHDijkstra.runDijkstra(src, trg, lambda, maxLambda);
            if (cspDist == Integer.MAX_VALUE){
                System.err.println("kein weg gefunden");
                continue;
            }
            backtrackCostResourceCSPCH(src,trg);
            costUpperBound = costCSPCH;
            touchedNodes += cspCHDijkstra.nofTouchedNodes;
            touchedEdges += cspCHDijkstra.edgeCount;
            iterations++;
            if(resourceCSPCH > resourceBound){
                //keinen schnellsten weg mit dem entsprechenden resourcebound gefunden
                lambdaOfBad = lambda;
                lambda = 0;
                cspDist = cspCHDijkstra.runDijkstra(src, trg, lambda, maxLambda);
                backtrackCostResourceCSPCH(src,trg);
                costLowerBound = costCSPCH;
                touchedNodes += cspCHDijkstra.nofTouchedNodes;
                touchedEdges += cspCHDijkstra.edgeCount;
                iterations++;
                if (resourceCSPCH > resourceBound){
                    System.err.println("gar keinen weg unter dem resourceBound gefunden");
                    continue;
                }
                lambdaOfGood = lambda;
                lambda = (lambdaOfGood + lambdaOfBad)/2;
                cspCHDijkstra.runDijkstra(src, trg, lambda, maxLambda);
                backtrackCostResourceCSPCH(src,trg);
                touchedNodes += cspCHDijkstra.nofTouchedNodes;
                touchedEdges += cspCHDijkstra.edgeCount;
                iterations++;
                while (lambdaOfBad - lambdaOfGood >= 1 ) {
                    if (resourceCSPCH <= resourceBound) {
                        lambdaOfGood = lambda;
                        costLowerBound = costCSPCH;
                    } else {
                        lambdaOfBad = lambda;
                        costUpperBound = costCSPCH;
                    }
                    if (lambdaOfBad - lambdaOfGood == 1){
                        break;
                    }
                    lambda = (lambdaOfGood + lambdaOfBad) / 2;
                    cspDist = cspCHDijkstra.runDijkstra(src, trg, lambda, maxLambda);
                    backtrackCostResourceCSPCH(src,trg);
                    touchedNodes += cspCHDijkstra.nofTouchedNodes;
                    touchedEdges += cspCHDijkstra.edgeCount;
                    iterations++;
                }
                cspCHDijkstra.runDijkstra(src, trg, lambda, maxLambda);
                backtrackCostResourceCSPCH(src,trg);

            } else {
                costLowerBound = costUpperBound;
            }


            cspEndTime = System.nanoTime();
            System.out.println("cspch;"+counter+";"+src+";"+trg+";"+resourceBound+";"+resourceCSPCH+";"+(cspEndTime-cspStartTime)+";"+iterations+";"+touchedNodes+";"+touchedEdges+";"+((float)costLowerBound/(float)costUpperBound));

        }
    }

    private void backtrackCostResourceCSP(int src, int trg){
        int curNode = trg;
        resourceCSP = 0;
        costCSP = 0;
        int curEdge;
        while (curNode != src) {
            curEdge = cspDijkstra.pred(curNode);
            resourceCSP += chGraph.edgeAltitudeDifference(curEdge);
            costCSP += chGraph.edgeWeight(curEdge);
            curNode = (chGraph.edgeSource(curEdge));
        }
    }

    private void backtrackCostResourceCSPCH(int src, int trg){
        int curNode = trg;
        resourceCSPCH = 0;
        costCSPCH = 0;
        int curEdge;
        while (curNode != src) {
            curEdge = cspCHDijkstra.pred(curNode);
            resourceCSPCH += chGraph.edgeAltitudeDifference(curEdge);
            costCSPCH += chGraph.edgeWeight(curEdge);
            curNode = (chGraph.edgeSource(curEdge));
        }
    }

    private int[] cspBacktrack(int src, int trg) {
        int curNode = trg;
        int nofRouteEdges = 0;
        int curEdge;
        while (curNode != src) {
            curEdge = cspDijkstra.pred(curNode);
            nofRouteEdges++;
            curNode = (cspDijkstra.myGraph.edgeSource(curEdge));
        }

        // Add them without values we set the values in the next step
        int [] path = new int[nofRouteEdges];

        // backtracking here

        if (src == trg)
            return path;
        int cur_node = trg;
        int cur_edge;

        for (int i = nofRouteEdges ; i > 0; --i){
            cur_edge = cspDijkstra.pred(cur_node);
            path[i-1] = cur_edge;
            cur_node = cspDijkstra.myGraph.edgeSource(cur_edge);
        }



        // add source node to the result.
        return path;

    }

    private int[] cspCHBacktrack(int src, int trg) {
        ArrayDeque<Integer> deque = new ArrayDeque<Integer>();
        ArrayDeque<Integer> pathQueue = new ArrayDeque<Integer>();
        int currNode = trg;
        int curEdge;
        int edgeSkippedA;
        int edgeSkippedB;

        while (currNode != src) {
            curEdge = cspCHDijkstra.pred(currNode);
            deque.addFirst(curEdge);
            currNode = chGraph.edgeSource(curEdge);
        }
        while (!deque.isEmpty()) {

            curEdge = deque.removeFirst();
            edgeSkippedA = chGraph.edgeSkippedA(curEdge);
            if (edgeSkippedA >= 0) {
                // We have a shortcut unpack it
                edgeSkippedB = chGraph.edgeSkippedB(curEdge);
                deque.addFirst(edgeSkippedB);
                deque.addFirst(edgeSkippedA);
            } else {
                // No shortcut remember it
                pathQueue.addLast(curEdge);
            }
        }

        int[] path = new int[pathQueue.size()];

        for (int i = 0 ; i < path.length; ++i){
           path[i] = pathQueue.removeFirst();
        }

        return path;

    }

    void printPathToGPX(int[] path, int trg){
            System.out.println("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>");
            System.out.println("<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:gpxx=\"http://www.garmin.com/xmlschemas/GpxExtensions/v3\" xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\" creator=\"Oregon 400t\" version=\"1.1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd http://www.garmin.com/xmlschemas/GpxExtensions/v3 http://www.garmin.com/xmlschemas/GpxExtensionsv3.xsd http://www.garmin.com/xmlschemas/TrackPointExtension/v1 http://www.garmin.com/xmlschemas/TrackPointExtensionv1.xsd\">");
            System.out.println("  <trk>\n" + "    <name>Example GPX Document</name>");

            System.out.println("<trkseg>");
            int curPoint = trg;
            if(path.length > 0){
                for (int i = 0; i < path.length; ++i) {
                    curPoint = chGraph.edgeSource(path[i]);
                    System.out.println("<trkpt lat=\"" + chGraph.xCoord(curPoint) + "\" lon=\"" + chGraph.yCoord(curPoint) + "\"></trkpt>");
                }
                curPoint = chGraph.edgeTarget(path[path.length-1]);
            }
            System.out.println("<trkpt lat=\"" + chGraph.xCoord(curPoint) + "\" lon=\"" + chGraph.yCoord(curPoint) + "\"></trkpt>");
            System.out.println("</trkseg>\n");
            System.out.println("</trk>\n</gpx>");
    }
}
