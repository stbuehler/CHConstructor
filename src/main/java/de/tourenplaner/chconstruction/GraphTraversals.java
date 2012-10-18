/*
 * (C) Copyright 2012 Dr. Stefan Funke
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2
 * of the License.
 */

package de.tourenplaner.chconstruction;

public class GraphTraversals {

    SGraph myGraph;
    boolean[] nodeTagged;
    int[] touchedNodes;
    int nofTouchedNodes;
    int lastSource;
    int[] bfsQueue;
    int bfsStart, bfsEnd;

    GraphTraversals(SGraph _myGraph) {
        myGraph = _myGraph;
        nodeTagged = new boolean[myGraph.nofNodes()];
        touchedNodes = new int[myGraph.nofNodes()];

        bfsQueue = new int[myGraph.nofNodes()];    // assume that queue has
        bfsStart = 0;                                // stuff in bfsQueue[start] until bfsQueue[end-1]
        bfsEnd = 0;

        for (int i = 0; i < myGraph.nofNodes(); i++) {
            nodeTagged[i] = false;
        }
        lastSource = -1;
    }

    void pushBFS(int v) {
        if (!nodeTagged[v]) {
            nodeTagged[v] = true;
            touchedNodes[nofTouchedNodes++] = v;
        }
        bfsQueue[bfsEnd] = v;
        bfsEnd++;
        if (bfsEnd == myGraph.nofNodes())
            bfsEnd = 0;
    }

    int popBFS() {
        assert (bfsStart != bfsEnd);
        int result = bfsQueue[bfsStart];
        bfsStart++;
        if (bfsStart == myGraph.nofNodes())
            bfsStart = 0;
        return result;
    }

    void runBFS(int src) {
        if ((lastSource != src)) {
            // clean up previously touched nodes
            for (int i = 0; i < nofTouchedNodes; i++) {
                nodeTagged[touchedNodes[i]] = false;
            }
            nofTouchedNodes = 0;

        }
        lastSource = src;
        pushBFS(src);
        while (bfsStart != bfsEnd) {
            int curNode = popBFS();
            for (int j = 0; j < myGraph.nofOutEdges(curNode); j++) {
                int curEdge = myGraph.outEdgeID(curNode, j);
            }
        }
    }

}
