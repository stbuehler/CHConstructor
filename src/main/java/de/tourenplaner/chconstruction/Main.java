package de.tourenplaner.chconstruction;

import org.apache.commons.cli.*;

import java.io.*;
import java.util.Random;


public class Main {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
        CommandLineParser parser = new GnuParser();
        Options options = new Options();

        options.addOption("t","text", false, "Use text mode graph file reading for human readable graph data");
        options.addOption("i", "input-file", true, "The graph file to read from, use - for standard input");
        options.addOption("o", "output-file", true, "The graph file to write the result to, use - for standard output");
        options.addOption("h", "help", false, "Show this help message");

        BufferedInputStream istream;
        try {
            CommandLine cmd = parser.parse(options, args);
            if (cmd.hasOption('h')){
                HelpFormatter fmt = new HelpFormatter();
                fmt.printHelp("chconstructor", options);
                System.exit(0);
            }
            if (!cmd.hasOption("i")){
                System.out.println("No input file specified, this is mandatory");
                HelpFormatter fmt = new HelpFormatter();
                fmt.printHelp("chconstructor", options);
                System.exit(0);
            }
           if (cmd.getOptionValue('i').equals("-")){
               istream = new BufferedInputStream(System.in);
           } else {
               istream = new BufferedInputStream(new FileInputStream(cmd.getOptionValue('i')));
           }

            RAMGraph ramGraph=new RAMGraph();
            if (cmd.hasOption('t')){
                ramGraph.readGTXT(istream);
            } else {
                ramGraph.readBIN(istream);
            }

            ramGraph.sanityCheck();


            RAMGraph ramGraph2=ramGraph.pruneGraph();

            Dijkstra myDijkstra=new Dijkstra(ramGraph);
            BDDijkstra myBDDijkstra=new BDDijkstra(ramGraph2);


            int src=11, trg=300000;
            Random generator=new Random();
            for(int i=0; i<2; i++)
            {
                long curTime=System.currentTimeMillis();
                int dist=myDijkstra.runDijkstra(src,trg);
                long timeDelta=System.currentTimeMillis()-curTime;
                System.out.println("Distance from "+src+" to "+trg+" is "+dist+" in time "+timeDelta);

                curTime=System.currentTimeMillis();
                int dist2=myBDDijkstra.runDijkstra(src, trg);
                timeDelta=System.currentTimeMillis()-curTime;
                System.out.println("BDDistance from "+src+" to "+trg+" is "+dist2+" in time "+timeDelta);
                System.out.println();

                // myDijkstra.printGeoPath(trg);
                assert(dist==dist2);

                src=generator.nextInt(ramGraph.nofNodes());
                trg=generator.nextInt(ramGraph.nofNodes());
            }
            ramGraph=ramGraph2;

            CHConstructor myCH=new CHConstructor(ramGraph);
            long overallTime=System.currentTimeMillis();
            long curTime, timeDelta;

            System.out.println("Starting Contraction!");
            for(int k=0; k<300; k++)
            {
                System.out.println("*************************************** "+(System.currentTimeMillis()-overallTime));
                curTime=System.currentTimeMillis();
                int n=myCH.contractLevel(k);
                timeDelta=System.currentTimeMillis()-curTime;
                System.out.println("Level "+k+" contraction was "+timeDelta+" having "+n+" nodes");
                if (n<=1)
                    k=Integer.MAX_VALUE-1;
            }



            // now turn myCHgraph into usable graph structure (!)
            // RAMGraph graphCH=myCH.myCHGraph.compressGraph();
            RAMGraph graphCH=myCH.myCHGraph.rearrangeGraph();

            System.out.println("==============================================");
            System.out.println("Total contraction time: "+(System.currentTimeMillis()-overallTime));


            graphCH.sanityCheck();


            CHDijkstra myCHDijkstra=new CHDijkstra(graphCH);
            myCHDijkstra.checkCHreqs();
            graphCH.setCHShortCuts();
            BufferedOutputStream ostream;
            if (!cmd.hasOption('o')){
                ostream = new BufferedOutputStream(new FileOutputStream("graph_out.gbin"));
            } else {
                if(cmd.getOptionValue('o').equals("-")){
                    ostream = new BufferedOutputStream(System.out);
                } else {
                    ostream = new BufferedOutputStream(new FileOutputStream(cmd.getOptionValue('o')));
                }
            }

            graphCH.writeBIN(ostream);

            myDijkstra=new Dijkstra(ramGraph);
            int minDist=Integer.MAX_VALUE;

            for(int i=0; i<100; i++)
            {
                //if ((graphCH.level(src)>0)&&(graphCH.level(trg)>0))
                {
                    curTime=System.nanoTime();
                    int dist=myDijkstra.runDijkstra(graphCH.altNodeID(src),graphCH.altNodeID(trg));
                    timeDelta=(System.nanoTime()-curTime)/1000;


                    curTime=System.nanoTime();
                    int dist2=myCHDijkstra.runDijkstra(src, trg);
                    long timeDelta2=(System.nanoTime()-curTime)/1000;
                    {
                        minDist=dist;
                        System.out.println("Distance from "+graphCH.altNodeID(src)+" to "+graphCH.altNodeID(trg)+" is "+dist+" in time "+timeDelta);
                        System.out.println("CH-Distance in CH from "+src+" to "+trg+" is "+dist2+" in time "+timeDelta2);
                        System.out.println("Levels are "+graphCH.level(src)+"/"+graphCH.level(trg));
                        if (dist==Integer.MAX_VALUE)
                            System.out.println("*******************************************************");
                        // myDijkstra.printPath(trg);
                        System.out.println();
                        assert(dist==dist2);
                    }

                    src=generator.nextInt(ramGraph.nofNodes());
                    trg=generator.nextInt(ramGraph.nofNodes());
                }

            }

        } catch (FileNotFoundException fnf){
            System.err.println("Could not open the input file "+fnf.getMessage());
        } catch (IOException ex){
            System.err.println("There was an IOException "+ex.getMessage());
        } catch (ParseException ps){
            System.err.println("Unexpected parse exception when reading command line "+ps.getMessage());
        }
    }

}
