/*
 * (C) Copyright 2012 Dr. Stefan Funke
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2
 * of the License.
 */

package de.tourenplaner.chconstruction;

import de.tourenplaner.chconstruction.graph.RAMGraph;
import de.tourenplaner.chconstruction.graphio.*;
import org.apache.commons.cli.*;

import java.io.*;
import java.util.Random;


public class Main {

    private static void preCHBenchTest(RAMGraph graph, RAMGraph prunedGraph) {
        Dijkstra myDijkstra = new Dijkstra(graph);
        BDDijkstra myBDDijkstra = new BDDijkstra(prunedGraph);
        Random generator = new Random();
        generator.setSeed(42);
        int src = generator.nextInt(graph.nofNodes());
        int trg = generator.nextInt(graph.nofNodes());
        for (int i = 0; i < 2; i++) {
            long curTime = System.currentTimeMillis();
            int dist = myDijkstra.runDijkstra(src, trg);
            long timeDelta = System.currentTimeMillis() - curTime;
            System.err.println("Distance from " + src + " to " + trg + " is " + dist + " in time " + timeDelta);

            curTime = System.currentTimeMillis();
            int dist2 = myBDDijkstra.runDijkstra(src, trg);
            timeDelta = System.currentTimeMillis() - curTime;
            System.err.println("BDDistance from " + src + " to " + trg + " is " + dist2 + " in time " + timeDelta);
            System.err.println();

            // myDijkstra.printGeoPath(trg);
            assert (dist == dist2);

            src = generator.nextInt(graph.nofNodes());
            trg = generator.nextInt(graph.nofNodes());
        }
    }

    private static void withChBench(RAMGraph prunedGraph, RAMGraph graphCH) {
        long curTime;
        long timeDelta;
        Random generator = new Random();
        generator.setSeed(42);
        int src = generator.nextInt(prunedGraph.nofNodes());
        int trg = generator.nextInt(prunedGraph.nofNodes());
        CHDijkstra myCHDijkstra = new CHDijkstra(graphCH);
        myCHDijkstra.checkCHreqs();
        Dijkstra myDijkstra = new Dijkstra(prunedGraph);
        int minDist = Integer.MAX_VALUE;

        for (int i = 0; i < 100; i++) {
            //if ((graphCH.getLevel(src)>0)&&(graphCH.getLevel(trg)>0))

            curTime = System.nanoTime();
            int dist = myDijkstra.runDijkstra(graphCH.getAltNodeID(src), graphCH.getAltNodeID(trg));
            timeDelta = (System.nanoTime() - curTime) / 1000;


            curTime = System.nanoTime();
            int dist2 = myCHDijkstra.runDijkstra(src, trg);
            long timeDelta2 = (System.nanoTime() - curTime) / 1000;

            minDist = dist;
            System.err.println("Distance from " + graphCH.getAltNodeID(src) + " to " + graphCH.getAltNodeID(trg) + " is " + dist + " in time " + timeDelta);
            System.err.println("CH-Distance in CH from " + src + " to " + trg + " is " + dist2 + " in time " + timeDelta2);
            System.err.println("Levels are " + graphCH.getLevel(src) + "/" + graphCH.getLevel(trg));
            if (dist == Integer.MAX_VALUE)
                System.err.println("*******************************************************");
            // myDijkstra.printPath(trg);
            System.err.println();
            assert (dist == dist2);


            src = generator.nextInt(prunedGraph.nofNodes());
            trg = generator.nextInt(prunedGraph.nofNodes());
        }

    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        CommandLineParser parser = new GnuParser();
        Options options = new Options();

        options.addOption("if", "input-format", true, "Choose from textfunk, textsabine, text, standard");
        options.addOption("of", "output-format", true, "Choose from bintour, textfunk, texttour, texttourcsp, offlinetp");
        options.addOption("i", "input-file", true, "The graph file to read from, use - for standard input");
        options.addOption("o", "output-file", true, "The graph file to write the result to, use - for standard output");
        options.addOption("co", "core-file", true, "The filename for the core file");
        options.addOption("ml", "max-level", true, "The maximum level for the core graph (default 40)");
        options.addOption("t", "ch-type", true, "Choose from cspch, ch");
        options.addOption("h", "help", false, "Show this help message");
        options.addOption("b", "bench", false, "Run benchmarks");

        BufferedInputStream istream;
        String inputFormat = "texttour";
        String outputFormat = "texttour";
        String chType = "ch";

        try {
            CommandLine cmd = parser.parse(options, args);
            if (cmd.hasOption('h')) {
                HelpFormatter fmt = new HelpFormatter();
                fmt.printHelp("chconstructor", options);
                return;
            }
            if (!cmd.hasOption("i")) {
                System.err.println("No input file specified, this is mandatory");
                HelpFormatter fmt = new HelpFormatter();
                fmt.printHelp("chconstructor", options);
                return;
            }
            if (cmd.hasOption("t")){
                chType=cmd.getOptionValue("t");
            }
 
            if (cmd.hasOption("if")) {
                inputFormat = cmd.getOptionValue("if");
            }

            if (cmd.hasOption("of")) {
                outputFormat = cmd.getOptionValue("of");
            }

            if (cmd.getOptionValue('i').equals("-")) {
                istream = new BufferedInputStream(System.in);
            } else {
                istream = new BufferedInputStream(new FileInputStream(cmd.getOptionValue('i')));
            }

            RAMGraph ramGraph;
            if (inputFormat.equals("textfunk")) {
                ramGraph = new GraphReaderTXTFunke().createRAMGraph(istream);
            } else if (inputFormat.equals("textsabine")) {
                ramGraph = new GraphReaderTXTSabine().createRAMGraph(istream);
            } else if (inputFormat.equals("text")) {
                ramGraph = new GraphReaderTXT().createRAMGraph(istream);
            } else if (inputFormat.equals("standard")){
                ramGraph = new GraphReaderNewStandard().createRAMGraph(istream);
            } else {
                System.err.println("Unknown input format " + inputFormat);
                return;
            }

            ramGraph.safeWeights();
            ramGraph.sanityCheck();
            RAMGraph prunedGraph = ramGraph.pruneGraph();
            if(cmd.hasOption("b"))
                preCHBenchTest(ramGraph, prunedGraph);
            // let GC know ramGraph is collectable
            ramGraph = null;

            Constructor myCH;
            if (chType.equals("ch")){
                myCH = new CHConstructor(prunedGraph);
            } else if (chType.equals("cspch")) {
                myCH = new CSPCHConstructor(prunedGraph);
            }  else {
                System.err.println("Unknown ch-type " + inputFormat);
                return;
            }


            long overallTime = System.currentTimeMillis();
            long curTime, timeDelta;

            System.err.println("Starting Contraction!");
            for (int k = 0; ; k++) {
                System.err.println("*************************************** " + (System.currentTimeMillis() - overallTime));
                curTime = System.currentTimeMillis();
                int n = myCH.contractLevel(k);
                timeDelta = System.currentTimeMillis() - curTime;
                System.err.println("Level " + k + " contraction was " + timeDelta + " having " + n + " nodes");

                //99%
                //if (n <= 1  || (n<prunedGraph.nofNodes()/100))
                //99,4 %
                //if (n <= 1  || (n<prunedGraph.nofNodes()*3/500))
                //99,5%
                //if (n <= 1  || (n<prunedGraph.nofNodes()/200))
                //99,8%
                //if (n <= 1  || (n<prunedGraph.nofNodes()/500))
                //99,9%
                //if (n <= 1  || (n<prunedGraph.nofNodes()/1000))
                //100%
                if (n <= 1)
                    break;
            }


            // now turn myCHgraph into usable graph structure (!)
            // RAMGraph graphCH=myCH.myCHGraph.compressGraph();
            RAMGraph graphCH = myCH.myCHGraph.rearrangeGraph();

            System.err.println("==============================================");
            System.err.println("Total contraction time: " + (System.currentTimeMillis() - overallTime));


            graphCH.sanityCheck();
            graphCH.printLevelStats();
            graphCH.setCHShortCuts();
            BufferedOutputStream ostream;
            if (!cmd.hasOption('o')) {
                ostream = new BufferedOutputStream(new FileOutputStream("graph_out.gbin"));
            } else {
                if (cmd.getOptionValue('o').equals("-")) {
                    ostream = new BufferedOutputStream(System.out);
                } else {
                    ostream = new BufferedOutputStream(new FileOutputStream(cmd.getOptionValue('o')));
                }
            }

            if (outputFormat.equals("texttour")) {
                new GraphWriterTXTTourenplaner().writeRAMGraph(ostream, graphCH);
            } else if (outputFormat.equals("texttourcsp")) {
                new GraphWriterTXTTourenplanerCSP().writeRAMGraph(ostream, graphCH);
            } else if (outputFormat.equals("textfunk")) {
                new GraphWriterTXTFunke().writeRAMGraph(ostream, graphCH);
            } else if (outputFormat.equals("bintour")) {
                new GraphWriterBinaryTourenplaner().writeRAMGraph(ostream, graphCH);
            } else if (outputFormat.equals("offlinetp")) {
                new GraphWriterOfflineToureNPlaner().writeRAMGraph(ostream, graphCH);
            } else {
                System.err.println("Unknown output format " + outputFormat);
                return;
            }

            if (cmd.hasOption("co")){
                int maxLevel = 40;
                if (cmd.hasOption("ml"))
                    maxLevel = Integer.parseInt(cmd.getOptionValue("ml"));
                new CoreExporter().exportCore(graphCH, new FileOutputStream(cmd.getOptionValue("co")), maxLevel);
            }
            if (chType.equals("cspch")) {
                CSPGraphInspector inspectorCH= new CSPGraphInspector(prunedGraph, graphCH);
                inspectorCH.inspectGraph();
                //inspectorCH.inspectGraphCSP();
                //inspectorCH.inspectGraphCSPCH();
            }else{
                if(cmd.hasOption("b"))
                    withChBench(prunedGraph, graphCH);
            }



        } catch (FileNotFoundException fnf) {
            System.err.println("Could not open the input file " + fnf.getMessage());
        } catch (IOException ex) {
            System.err.println("There was an IOException " + ex.getMessage());
        } catch (ParseException ps) {
            System.err.println("Unexpected parse exception when reading command line " + ps.getMessage());
        }
    }

}
