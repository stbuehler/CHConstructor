/*
 * (C) Copyright 2012 Dr. Stefan Funke
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2
 * of the License.
 */

package de.tourenplaner.chconstruction;
/* Graph classes 
 * Desired Properties: 	- static
 * 						- explicit in- and outedge lists
 * 						- small memory footprints
 * Implementation:		- array based
 * 						- edges are stored once in a large list of edges
 * 						- two arrays inEdges/outEdges refer to this large list of edges
 *
 * RAM and ExtMEMORY
 */

public abstract class SGraph {

    // interface for accessing Graph data
    abstract int nofEdges();

    abstract int nofNodes();


    abstract float getLat(int nodeID);

    abstract float getLon(int nodeID);

    abstract int getAltNodeID(int nodeID);

    abstract int getHeight(int nodeID);

    abstract int getOSMID(int nodeID);

    abstract int getLevel(int nodeID);


    abstract int nofOutEdges(int nodeID);

    abstract int nofInEdges(int nodeID);

    abstract int outEdgeID(int nodeID, int edgePos);    // returns edge ID of edgePos-th outEdge of nodeID

    abstract int inEdgeID(int nodeID, int edgePos);    // returns edge ID of edgePos-th inEdge of nodeID


    abstract int getSource(int edgeID);

    abstract int getTarget(int edgeID);

    abstract int getWeight(int edgeID);

    abstract int getEuclidianLength(int edgeID);

    abstract int getAltitudeDifference(int edgeID);

    abstract int getSkippedA(int edgeID);

    abstract int getSkippedB(int edgeID);
}
