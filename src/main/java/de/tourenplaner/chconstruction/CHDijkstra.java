package de.tourenplaner.chconstruction;

import java.util.PriorityQueue;

public class CHDijkstra extends BDDijkstra {
	
	CHDijkstra(SGraph _myGraph)
	{
		super(_myGraph);
	}
	
	int runDijkstra(int src, int trg)	// returns distance from src to trg
	{

		// clean up previously touched nodes
		for(int i=0; i<nofTouchedNodes; i++)
		{
			distFwd[touchedNodes[i]]=distBwd[touchedNodes[i]]=Integer.MAX_VALUE;
			settledFwd[touchedNodes[i]]=settledBwd[touchedNodes[i]]=false;
		}
		nofTouchedNodes=0;
		myQueue.clear();
		// start with src
		labelFwd(src,0);
		labelBwd(trg,0);
		
		int bestDist=Integer.MAX_VALUE;
	
		int edgeCount=0;
		// otherwise we have to process pq until settling trg
		boolean phaseFinished=false;
		while ((!myQueue.isEmpty())&&(phaseFinished==false))
		{
			//System.out.print(".");
			BDPQElement cur=myQueue.remove();
			int cur_dist=cur.key;
			int cur_node=cur.value;
			int cur_side=cur.queue;
			
			//if (cur_dist>bestDist)
			//	phaseFinished=true;
			
			if (cur_side==0)	// we are in forward search
			{
				if (cur_dist==distFwd[cur_node])
				{
					settledFwd[cur_node]=true;
			
					boolean stalled=false;
					
					// check for stalling (if there is a node tmp_node (ABOVE) and an edge (tmp_node,cur_node)
					// which sum to a smaller distance (!)
					//for(int j=0; j<myGraph.nofInEdges(cur_node); j++)
					for(int j=myGraph.nofInEdges(cur_node)-1; j>=0; j--)
					{
						int tmp_edge=myGraph.inEdgeID(cur_node,j);
						int tmp_wgt=myGraph.edgeWeight(tmp_edge);
						int tmp_node=myGraph.edgeSource(tmp_edge);
						if (distFwd[cur_node]-tmp_wgt>distFwd[tmp_node])
						{
							stalled=true;
							break;
						}
						if (myGraph.level(tmp_node)<myGraph.level(cur_node))
							break;
					}
					
					if ((settledBwd[cur_node]) &&(distFwd[cur_node]+distBwd[cur_node]<bestDist))
						bestDist=distFwd[cur_node]+distBwd[cur_node];
						
					if (stalled==false)
						for (int i=myGraph.nofOutEdges(cur_node)-1; i>=0; i--)
						{
							int cur_edge=myGraph.outEdgeID(cur_node, i);
							int cur_trg=myGraph.edgeTarget(cur_edge);
							int cur_weight=myGraph.edgeWeight(cur_edge);
							if (myGraph.level(cur_trg)>=myGraph.level(cur_node))
								edgeCount++;
							else
								break;
							if ((myGraph.level(cur_trg)>=myGraph.level(cur_node))&&(distFwd[cur_trg]>cur_dist+cur_weight))
							{
								labelFwd(cur_trg, cur_dist+cur_weight);
							}
						}
				}
			}
			else	// we are in backward search
			{
				if (cur_dist==distBwd[cur_node])
				{
					settledBwd[cur_node]=true;
					boolean stalled=false;
					
					// check for stalling: if there is a node ABOVE cur_node ...
					//for(int j=0; j<myGraph.nofOutEdges(cur_node); j++)
					for(int j=myGraph.nofOutEdges(cur_node)-1; j>=0; j--)
					{
						int tmp_edge=myGraph.outEdgeID(cur_node,j);
						int tmp_wgt=myGraph.edgeWeight(tmp_edge);
						int tmp_node=myGraph.edgeTarget(tmp_edge);
						if (distBwd[cur_node]-tmp_wgt>distBwd[tmp_node])
						{
							stalled=true;
							break;
						}
						if (myGraph.level(cur_node)>myGraph.level(tmp_node))
							break;
					}
					
					
					if ((settledFwd[cur_node]) &&(distFwd[cur_node]+distBwd[cur_node]<bestDist))
						bestDist=distFwd[cur_node]+distBwd[cur_node];
			
					if (stalled==false)
						for (int i=myGraph.nofInEdges(cur_node)-1; i>=0; i--)
						{
							int cur_edge=myGraph.inEdgeID(cur_node, i);
							int cur_trg=myGraph.edgeSource(cur_edge);
							int cur_weight=myGraph.edgeWeight(cur_edge);
							if (myGraph.level(cur_trg)>=myGraph.level(cur_node))
								edgeCount++;
							else
								break;
							if ((myGraph.level(cur_trg)>=myGraph.level(cur_node))&&(distBwd[cur_trg]>cur_dist+cur_weight))
							{
								labelBwd(cur_trg, cur_dist+cur_weight);
							}
						}
				}
			}
		}
		System.out.println("CH-BD has touched "+nofTouchedNodes+" and looked at "+edgeCount+" edges");
	
		if (1==0)
		{
			// ONLY FOR DEBUGGING
			// now check how many nodes bear correct distances
			int bestTmpDist=Integer.MAX_VALUE;
			Dijkstra myTmpDijkstraF=new Dijkstra(myGraph);
			Dijkstra myTmpDijkstraB=new BwDijkstra(myGraph);
			int fwdOK=0, fwdTotal=0, bwdOK=0, bwdTotal=0, bothOK=0;

			for(int i=0; i<nofTouchedNodes; i++)
			{
				//if ((i%100)==0)
				//	System.out.println(i+" ");
				int curNode=touchedNodes[i];
				boolean fwdUse=false, bwdUse=false;

				int fwdDist=distFwd[curNode];
				if (fwdDist!=Integer.MAX_VALUE)
				{
					fwdTotal++;
					myTmpDijkstraF.runDijkstra(src, curNode);
					assert(myTmpDijkstraF.dist[curNode]!=Integer.MAX_VALUE);
					if (myTmpDijkstraF.dist[curNode]==fwdDist)
					{
						if (myGraph.level(curNode)>80)
								System.out.print(curNode+"("+myGraph.level(curNode)+") ");
						fwdOK++;
						fwdUse=true;
					}
				}
				int bwdDist=distBwd[curNode];
				if (bwdDist!=Integer.MAX_VALUE)
				{
					bwdTotal++;
					myTmpDijkstraB.runDijkstra(trg,curNode);
					assert(myTmpDijkstraB.dist[curNode]!=Integer.MAX_VALUE);
					if (myTmpDijkstraB.dist[curNode]==bwdDist)
					{
						bwdOK++;
						bwdUse=true;
					}
				}
				if ((bwdDist!=Integer.MAX_VALUE)&&(fwdDist!=Integer.MAX_VALUE))
				{
					if (myTmpDijkstraF.dist[curNode]+myTmpDijkstraB.dist[curNode]<bestTmpDist)
						bestTmpDist=myTmpDijkstraF.dist[curNode]+myTmpDijkstraB.dist[curNode];
				}
				if (bwdUse||fwdUse)
					bothOK++;
			}
			System.out.println("\n Forward search: "+fwdOK+"/"+fwdTotal);
			System.out.println("Backward search: "+bwdOK+"/"+bwdTotal);
			System.out.println("Best Distance:"+bestTmpDist+" and total nodes: "+bothOK);
		}
		return bestDist;
		
	}
	void checkCHreqs()
    {
        for(int i=0; i<myGraph.nofNodes(); i++)
        {
            for(int j=0;j<myGraph.nofOutEdges(i)-1; j++)
            {
                int cur_edge=myGraph.outEdgeID(i,j);
                int next_edge=myGraph.outEdgeID(i,j+1);
                int trg_node=myGraph.edgeTarget(cur_edge);
                int next_trg_node=myGraph.edgeTarget(next_edge);
                assert(myGraph.level(trg_node)<=myGraph.level(next_trg_node));
            }
            for(int j=0;j<myGraph.nofInEdges(i)-1; j++)
            {
                int cur_edge=myGraph.inEdgeID(i,j);
                int next_edge=myGraph.inEdgeID(i,j+1);
                int src_node=myGraph.edgeSource(cur_edge);
                int next_src_node=myGraph.edgeSource(next_edge);
                assert(myGraph.level(src_node)<=myGraph.level(next_src_node));
            }
        }
        System.out.println("CH Reqs ok!");
    }
}
