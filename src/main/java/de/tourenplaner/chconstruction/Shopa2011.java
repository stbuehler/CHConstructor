package de.tourenplaner.chconstruction;

import java.util.Random;


public class Shopa2011 {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		System.out.println("Das ist das Demoprogramm für Shopa 2011!");
		
		RAMGraph ramGraph=new RAMGraph();
		
				
		
		//ramGraph.readGTXT("graph.gtxt");
		//ramGraph.writeBIN("graph.gbin");
		ramGraph.readBIN("graph.gbin");
		
		//ramGraph.readGTXT("15000K.txt.gtxt");
		//ramGraph.writeBIN("1500K.gbin");
		//ramGraph.readBIN("1500K.gbin");
		
		//ramGraph.readGTXT("DE.gtxt");
		//ramGraph.writeBIN("DE.gbin");
		//ramGraph.readBIN("DE.gbin");
		
		
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
		ramGraph2=new RAMGraph();
		
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
		

		// graphCH.writeGTXT("graphCH.gtxt");
		
		
		graphCH.sanityCheck();
		
		
		CHDijkstra myCHDijkstra=new CHDijkstra(graphCH);
		myCHDijkstra.checkCHreqs();
		graphCH.setCHShortCuts();
		graphCH.writeBIN("graph_ch.gbin");
		//graphCH.writeGTXT("graph_ch.gtxt");
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
					}
/*
					System.out.println("InEdges: ");
					for (int j=0; j<graphCH.nofInEdges(src); j++)
					{
						int curEdge=graphCH.inEdgeID(src, j);
						System.out.println(graphCH.edgeSource(curEdge)+"-"+graphCH.edgeTarget(curEdge)+" ");
					}
					System.out.println();
					System.out.println("OutEdges: ");
					for (int j=0; j<graphCH.nofOutEdges(src); j++)
					{
						int curEdge=graphCH.outEdgeID(src, j);
						System.out.println(graphCH.edgeSource(curEdge)+"-"+graphCH.edgeTarget(curEdge)+" ");
					}
					System.out.println();
	*/				
					assert(dist==dist2);
				}

				src=generator.nextInt(ramGraph.nofNodes());
				trg=generator.nextInt(ramGraph.nofNodes());
			}
		
	}

}
