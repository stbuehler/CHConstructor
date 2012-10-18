/*
 * (C) Copyright 2012 Peter Vollmer
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2
 * of the License.
 */

package de.tourenplaner.chconstruction;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * User: Peter Vollmer
 * Date: 10/8/12
 * Time: 12:56 PM
 */
public class GraphWriterBinaryFunke implements GraphWriter {
    @Override
    public void writeRAMGraph(OutputStream out, RAMGraph ramGraph) throws IOException {

        long curTime = System.currentTimeMillis();
        // Wrap the FileOutputStream with a DataOutputStream
        DataOutputStream data_out = new DataOutputStream(out);
        // write-out nof vertices and edges
        data_out.writeInt(ramGraph.nofNodes);
        data_out.writeInt(ramGraph.nofEdges);

        // write-out node data
        for (int i = 0; i < ramGraph.nofNodes; i++) {
            data_out.writeFloat(ramGraph.xCoord[i]);
            data_out.writeFloat(ramGraph.yCoord[i]);
        }
        for (int i = 0; i < ramGraph.nofNodes; i++) {
            data_out.writeInt(ramGraph.altID[i]);
        }
        for (int i = 0; i < ramGraph.nofNodes; i++) {
            data_out.writeInt(ramGraph.level[i]);
        }
        for (int i = 0; i < ramGraph.nofNodes + 1; i++) {
            data_out.writeInt(ramGraph.outEdgeOffset[i]);
        }
        for (int i = 0; i < ramGraph.nofNodes + 1; i++) {
            data_out.writeInt(ramGraph.inEdgeOffset[i]);
        }

        // write-out edge data
        for (int j = 0; j < ramGraph.nofEdges; j++) {
            data_out.writeInt(ramGraph.outEdgeList[j]);
        }
        for (int j = 0; j < ramGraph.nofEdges; j++) {
            data_out.writeInt(ramGraph.inEdgeList[j]);
        }
        for (int i = 0; i < ramGraph.nofEdges; i++) {
            data_out.writeInt(ramGraph.edgeSource[i]);
            data_out.writeInt(ramGraph.edgeTarget[i]);
            data_out.writeInt(ramGraph.edgeWeight[i]);
        }
        for (int i = 0; i < ramGraph.nofEdges; i++) {
            data_out.writeInt(ramGraph.edgeSkippedA[i]);
            data_out.writeInt(ramGraph.edgeSkippedB[i]);
        }
        out.flush();

        System.err.println("Wrote BIN file in time " + (System.currentTimeMillis() - curTime));
    }
}
