package de.tourenplaner.chconstruction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

/**
 * User: Peter Vollmer
 * Date: 12/3/12
 * Time: 10:45 AM
 */
public class GraphReaderTXTTourenplanerCSP implements GraphReader{
    @Override
    public RAMGraph createRAMGraph(InputStream in) throws IOException {
        int nofNodes;
        int nofEdges;


        long curTime = System.currentTimeMillis();

        BufferedReader inb = new BufferedReader(new InputStreamReader(in));
        String line = inb.readLine();
        while (line != null && line.trim().startsWith("#")) {
            line = inb.readLine();
        }
        nofNodes = line != null ? Integer.parseInt(line) : 0;

        line = inb.readLine();
        nofEdges = line != null ? Integer.parseInt(line) : 0;

        RAMGraph graph = new RAMGraph(nofNodes, nofEdges);
        float x, y;
        int height, level;
        String[] splittedLine;
        int iDCounter = 0;
        for (int i = 0; i < nofNodes; i++) {
            splittedLine = COMPILE.split(inb.readLine());
            x = Integer.parseInt(splittedLine[0])/10000000.F;
            y = Integer.parseInt(splittedLine[1])/10000000.F;
            height = Integer.parseInt(splittedLine[2]);
            level = Integer.parseInt(splittedLine[3]);
            graph.addNode(x, y, iDCounter, height, -1,level);
            iDCounter++;
            if ((i % (nofNodes / 10)) == 0) {
                System.err.print((10 * i / (nofNodes / 10) + "% "));
            }
        }

        int edgeSource, edgeTarget, edgeWeight, edgeAltDiff,edgeLength,edgeSkippedA, edgeSkippedB;
        for (int i = 0; i < nofEdges; i++) {
            splittedLine = COMPILE.split(inb.readLine());
            edgeSource = Integer.parseInt(splittedLine[0]);
            edgeTarget = Integer.parseInt(splittedLine[1]);
            edgeWeight = Integer.parseInt(splittedLine[2]);
            edgeLength = Integer.parseInt(splittedLine[3]);
            edgeAltDiff = Integer.parseInt(splittedLine[4]);
            edgeSkippedA = Integer.parseInt(splittedLine[5]);
            edgeSkippedB = Integer.parseInt(splittedLine[6]);


            if(edgeWeight == 0){
                edgeWeight = 1;
            }
            if (edgeAltDiff < 0) {
                edgeAltDiff = 0;
            }

            graph.addEdge(edgeSource, edgeTarget, edgeWeight, edgeLength, edgeAltDiff,edgeSkippedA,edgeSkippedB);

            if ((i % (nofEdges / 10)) == 0) {
                System.err.print((10 * i / (nofEdges / 10) + "% "));
            }
        }

        System.err.println("Parsing took " + (System.currentTimeMillis() - curTime));
        graph.setupOffsets();

        System.err.println("Read graph with " + nofNodes +
                " vertices and " + nofEdges + " edges in time " + (System.currentTimeMillis() - curTime) + "ms");
        return graph;
    }

    private static final Pattern COMPILE = Pattern.compile(" ");




}
