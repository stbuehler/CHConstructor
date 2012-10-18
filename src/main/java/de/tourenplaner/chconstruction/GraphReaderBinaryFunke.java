/*
 * (C) Copyright 2012 Peter Vollmer
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2
 * of the License.
 */

package de.tourenplaner.chconstruction;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * User: Peter Vollmer
 * Date: 10/8/12
 * Time: 12:52 PM
 */
public class GraphReaderBinaryFunke implements GraphReader {
    @Override
    public RAMGraph createRAMGraph(InputStream in) throws IOException {

        long curTime = System.currentTimeMillis();

        // ... wrap the stream
        DataInputStream data_in = new DataInputStream(in);

        int nofNodes, nofEdges;

        // read # vertices and edges
        nofNodes = data_in.readInt();
        nofEdges = data_in.readInt();

        RAMGraph graph = new RAMGraph(nofNodes, nofEdges);

        System.err.println("Reading " + nofNodes + " vertices and " + nofEdges +
                " edges");

        // read node data
        for (int i = 0; i < nofNodes; i++) {
            graph.xCoord[i] = data_in.readFloat();
            graph.yCoord[i] = data_in.readFloat();
        }
        for (int i = 0; i < nofNodes; i++) {
            graph.altID[i] = data_in.readInt();
        }
        for (int i = 0; i < nofNodes; i++) {
            graph.level[i] = data_in.readInt();
        }
        for (int i = 0; i < nofNodes + 1; i++) {
            graph.outEdgeOffset[i] = data_in.readInt();
        }
        for (int i = 0; i < nofNodes + 1; i++) {
            graph.inEdgeOffset[i] = data_in.readInt();
        }

        // write-out edge data
        for (int j = 0; j < nofEdges; j++) {
            graph.outEdgeList[j] = data_in.readInt();
        }
        for (int j = 0; j < nofEdges; j++) {
            graph.inEdgeList[j] = data_in.readInt();
        }

        for (int i = 0; i < nofEdges; i++) {
            graph.edgeSource[i] = data_in.readInt();
            graph.edgeTarget[i] = data_in.readInt();
            graph.edgeWeight[i] = data_in.readInt();
            if (graph.edgeWeight[i] <= 0)
                graph.edgeWeight[i] = 1;
        }
        for (int i = 0; i < nofEdges; i++) {
            graph.edgeSkippedA[i] = data_in.readInt();
            graph.edgeSkippedB[i] = data_in.readInt();
        }
        System.err.println("Read BIN file in time " + (System.currentTimeMillis() - curTime));


        return null;
    }
}
