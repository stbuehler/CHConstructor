/*
 * (C) Copyright 2012 Dr. Stefan Funke
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2
 * of the License.
 */

package de.tourenplaner.chconstruction;

public class BwDijkstra extends Dijkstra {

    BwDijkstra(SGraph myGraph) {
        super(myGraph);
        // TODO Auto-generated constructor stub
    }

    int runDijkstra(int src, int trg)    // returns distance from src to trg
    {
        if ((lastSource != src)) {
            // clean up previously touched nodes
            for (int i = 0; i < nofTouchedNodes; i++) {
                dist[touchedNodes[i]] = Integer.MAX_VALUE;
                settled[touchedNodes[i]] = false;
            }
            nofTouchedNodes = 0;
            myQueue.clear();
            // start with src
            label(src, 0, -1);
            lastSource = src;
        } else if (settled[trg]) {
            return dist[trg];
        }
        // otherwise we have to process pq until settling trg
        boolean targetFound = false;
        while ((!myQueue.isEmpty()) && !targetFound) {
            PQElement cur = myQueue.remove();
            int cur_dist = cur.key;
            int cur_node = cur.value;

            if (cur_dist == dist[cur_node]) {
                settled[cur_node] = true;
                if (cur_node == trg)
                    targetFound = true;
                for (int i = 0; i < myGraph.nofInEdges(cur_node); i++) {
                    int cur_edge = myGraph.inEdgeID(cur_node, i);
                    int cur_trg = myGraph.edgeSource(cur_edge);
                    int cur_weight = myGraph.edgeWeight(cur_edge);
                    if (dist[cur_trg] > cur_dist + cur_weight) {
                        label(cur_trg, cur_dist + cur_weight, cur_node);
                    }
                }
            }
        }
        // System.out.println("Dijkstra has touched "+nofTouchedNodes);
        return dist[trg];

    }
}
