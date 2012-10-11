/*
 * (C) Copyright 2012 Peter Vollmer
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2
 * of the License.
 */

package de.tourenplaner.chconstruction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

/**
 * User: Peter Vollmer
 * Date: 10/9/12
 * Time: 3:38 PM
 */
public class GraphReaderTXT implements GraphReader{
    @Override
    public RAMGraph createRAMGraph(InputStream in) throws IOException {

        int nofNodes;
        int nofEdges;


        long curTime=System.currentTimeMillis();

        BufferedReader inb = new BufferedReader(new InputStreamReader(in));
        String line = inb.readLine();
        while (line != null && line.trim().startsWith("#")) {
            line = inb.readLine();
        }
        nofNodes = line != null ? Integer.parseInt(line) : 0;

        line = inb.readLine();
        nofEdges = line != null ? Integer.parseInt(line) : 0;

        RAMGraph graph = new RAMGraph(nofNodes,nofEdges);
        float x,y;
        int altID,OSMID,height;
        String[] splittedLine;
        for(int i=0; i<nofNodes; i++) {
            splittedLine = COMPILE.split(inb.readLine());
            //altID[i]=i;
            if (splittedLine.length == 5){
                altID=Integer.parseInt(splittedLine[0]);
                OSMID=Integer.parseInt(splittedLine[1]);
                x=Float.parseFloat(splittedLine[2]);
                y=Float.parseFloat(splittedLine[3]);
                height=Integer.parseInt(splittedLine[4]);

                graph.addNode(x,y,altID,height,OSMID);

                if ((i%(nofNodes/10))==0)
                {
                    System.out.print((10*i/(nofNodes/10)+"% "));
                }
            }else {
                graph.nofNodes--;
            }
        }

        int edgeSource,edgeTarget,edgeWeight,edgeType;
        for(int i=0; i<nofEdges; i++) {
            splittedLine = COMPILE.split(inb.readLine());
            if (splittedLine.length == 4){
                edgeSource=Integer.parseInt(splittedLine[0]);
                edgeTarget=Integer.parseInt(splittedLine[1]);
                edgeWeight=Integer.parseInt(splittedLine[2]);
                edgeType =Integer.parseInt(splittedLine[3]);
                graph.addEdge(edgeSource,edgeTarget,edgeWeight,edgeType);

                if ((i%(nofEdges/10))==0)
                {
                    System.out.print((10*i/(nofEdges/10)+"% "));
                }
            }else{
                graph.nofEdges--;
            }
        }

        if (nofNodes != graph.nofNodes() | nofEdges != graph.nofEdges()){
            graph = new RAMGraph(graph);
        }

        System.out.println("Parsing took "+(System.currentTimeMillis()-curTime));
        graph.setupOffsets();

        System.out.println("Read graph with "+nofNodes+
                " vertices and "+nofEdges+" edges in time "+(System.currentTimeMillis()-curTime)+"ms");
        return graph;
    }

    private static final Pattern COMPILE = Pattern.compile(" ");
}
