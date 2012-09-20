import java.util.PriorityQueue;

/* computes contraction hierarchy for the given graph
 * KEEPS the given order and the altID
 * IDs of the original graph
 * ONLY WORKS with RAMGraph (!)
 * 
 * tempGraph stores current residual graph
 * myCHGraph stores graph with currently added Shortcuts 
 * 
 * levels are set such that myCHGraph allows CH-SP-Queries at any time (after setting up offsets)
 * 
 * skipped edges are set after the very end only !!!
 */

public class CHConstructor {
	
	RAMGraph myGraph;		// original graph
	
	RAMGraph myCHGraph;	// store the augmented result graph
	
	RAMGraph tempGraph; // store the residual graph
	
	CHConstructor (RAMGraph _myGraph)
	{
		myGraph=_myGraph;
		myCHGraph=new RAMGraph();
		tempGraph=new RAMGraph();
		myCHGraph.createGraphSkeleton(myGraph.nofNodes(), 3*myGraph.nofEdges());	/// KONSTANTE GRUSEL!!!!!!!
		tempGraph.createGraphSkeleton(myGraph.nofNodes(), myGraph.nofEdges());
		for(int i=0; i<myGraph.nofNodes(); i++)	// first add the original graph
		{
			myCHGraph.addNode(myGraph.xCoord(i), _myGraph.yCoord(i), myGraph.altNodeID(i), Integer.MAX_VALUE);
			tempGraph.addNode(myGraph.xCoord(i), _myGraph.yCoord(i), i, Integer.MAX_VALUE); // here alt denotes ID
		}
		for(int j=0; j<myGraph.nofEdges(); j++)
		{
			int orgSrc=myGraph.edgeSource(j), orgTrg=myGraph.edgeTarget(j), orgWeight=myGraph.edgeWeight(j);
			myCHGraph.addEdge(orgSrc, orgTrg, orgWeight, -1, -1);
			tempGraph.addEdge(orgSrc, orgTrg, orgWeight, -1, -1);
		}
		tempGraph.setupOffsets();
		
		// tempGraph.sanityCheck();
	}
	
	int contractNode(int curNode, Dijkstra myDijkstra, int [] srcSC, int [] trgSC, int[] wgtSC, int boundSC)	// return number of computed SCs
	{																											// stops if boundSC is reached
		int nofSC=0;
		for(int i=0; i<tempGraph.nofInEdges(curNode); i++)
		{
			int curSrcEdge=tempGraph.inEdgeID(curNode,i);
			int curSrc=tempGraph.edgeSource(curSrcEdge);
			for (int j=0; j<tempGraph.nofOutEdges(curNode); j++)
			{
				int curTrgEdge=tempGraph.outEdgeID(curNode, j);
				int curTrg=tempGraph.edgeTarget(curTrgEdge);
				int weightSC=tempGraph.edgeWeight(curSrcEdge)+tempGraph.edgeWeight(curTrgEdge);
				int d=myDijkstra.runDijkstra(curSrc, curTrg);
				//if (d==weightSC) // better: check if pred[curTrg]==curNode and pred[curNode]==curSrc
				if (((myDijkstra.pred(curTrg)==curNode)&&(myDijkstra.pred(curNode)==curSrc)))
				{
					srcSC[nofSC]=curSrc;
					trgSC[nofSC]=curTrg;
					wgtSC[nofSC]=weightSC;
					nofSC++;
				}
				if (nofSC==boundSC) return boundSC;
			}
		}
		return nofSC;
	}
	
	int contractLevel(int newLevel)	// contracts an independent set of the current tempGraph 
	{
		PriorityQueue<PQElement> degreePQ=new PriorityQueue<PQElement>();
		boolean [] stillIndep=new boolean[tempGraph.nofNodes()];
		boolean [] contracted=new boolean[tempGraph.nofNodes()];
		int [] candidates=new int[tempGraph.nofNodes()];
		int [] candSCoffset=new int[tempGraph.nofNodes()+1];		// offsets into list of shortcuts
		int nofCandidates=0;
		
		// create priority queue with nodes acc. to their degrees (MULTIPLIED)
		for(int i=0; i<tempGraph.nofNodes(); i++)
		{
			stillIndep[i]=true;
			contracted[i]=false;
			int degree=tempGraph.nofInEdges(i)*tempGraph.nofOutEdges(i);
			degreePQ.add(new PQElement(degree,i));
		}
		
		PQElement curEl;
		
		// now pick independent set as candidates; greedy, starting with small degrees
		int degSum=0;
		while (!degreePQ.isEmpty())
		{
			curEl=degreePQ.remove();
			int curNode=curEl.value;
			if (stillIndep[curNode]==true)
			{
				degSum+=curEl.key;
				candidates[nofCandidates]=curNode;
				nofCandidates++;
				for(int j=0; j<tempGraph.nofInEdges(curNode); j++)
				{
					int edgeID=tempGraph.inEdgeID(curNode, j);
					int src=tempGraph.edgeSource(edgeID);
					stillIndep[src]=false;
				}
				for(int j=0; j<tempGraph.nofOutEdges(curNode); j++)
				{
					int edgeID=tempGraph.outEdgeID(curNode, j);
					int trg=tempGraph.edgeTarget(edgeID);
					stillIndep[trg]=false;
				}
			}
		}
		System.out.println("We have an IS of size "+nofCandidates);
		//PQElement curEl=degreePQ.peek();
		int boundSC=degSum/nofCandidates+1;
		// we know that we can find a node which produces at most boundSC shortcuts !!!
		if (boundSC<6)
			boundSC=6;
		
		System.out.println("boundSC="+boundSC);
		
		
		int allSCUB=tempGraph.nofEdges();
		if (allSCUB<boundSC*nofCandidates)
			allSCUB=boundSC*nofCandidates;
			
		// allocate memory for all SCs
		int [] srcSCall=new int[allSCUB];
		int [] trgSCall=new int[allSCUB];
		int [] wgtSCall=new int[allSCUB];
				
		
		
		
		
		// instantiate Dijkstra
		Dijkstra myDijkstra=new Dijkstra(tempGraph);
		
		int tentNofSC=0;
		candSCoffset[0]=0;	
		
		// now we try to contract the nodes of the independent set
		PriorityQueue<PQElement> contractionPQ;
		
		int sumED=0;
		int validED=0;
		
		boundSC=(boundSC+4)/2;
		if (newLevel<3)
			boundSC=2;
		int candBound=nofCandidates;	// how many candidates to look at
		int validBound=5*candBound/6;		// how many of the considered candidates to fully evaluate (at least)
		int contractBound=validBound;	// how many of the evaluated candidates to contract (at least)
		
		do
		{
			contractionPQ=new PriorityQueue<PQElement>();
			boundSC=boundSC*2;
			System.out.print("\n Current boundSC: "+boundSC+"  ");
			validED=0;
			sumED=0;
			tentNofSC=0;

			// temporary memory for single contraction 
			int [] srcSC=new int[boundSC];
			int [] trgSC=new int[boundSC];
			int [] wgtSC=new int[boundSC];
			
				
			for(int i=0; i<candBound; i++)
			{
				int nofSC=contractNode(candidates[i], myDijkstra, srcSC, trgSC, wgtSC, boundSC);
				int edgeDiff=nofSC-tempGraph.nofInEdges(candidates[i])-tempGraph.nofOutEdges(candidates[i]);
				if (nofSC<boundSC)
				{
					sumED+=edgeDiff;
					validED++;
				}

				for(int j=0; j<nofSC; j++)
				{
					srcSCall[tentNofSC]=srcSC[j];
					trgSCall[tentNofSC]=trgSC[j];
					wgtSCall[tentNofSC]=wgtSC[j];					
					tentNofSC++;
				}
				contractionPQ.add(new PQElement(edgeDiff, i));
				candSCoffset[i+1]=tentNofSC;

				if ((i%(nofCandidates/10+1))==0)
				{
					System.out.print((10*i/(nofCandidates/10+1)+"% "));
					System.out.print("("+nofSC+"/"+edgeDiff+") ");

				}
			}
		} while (validED<validBound);
		
		int newNofNodes=0;
		int newNofEdges=0;
		int realContract=0;
		int totalNofSC=0;

		// allocate memory for final SCs
		int [] srcSCfinal=new int[allSCUB];
		int [] trgSCfinal=new int[allSCUB];
		int [] wgtSCfinal=new int[allSCUB];
		
		int avgED=sumED/validED+1;
		System.out.println("\n AvgED="+avgED+" validED="+validED);
		
		
		while (!contractionPQ.isEmpty())
		{
			PQElement curCand=contractionPQ.remove();
			int curNode=curCand.value;
			int curED=curCand.key;
			int curNofSC=candSCoffset[curNode+1]-candSCoffset[curNode];
			
			if (	(curNofSC<boundSC) &&								// we contract if ED<=0 but at least 3/4 of valid candidates
					((curED<=0) || (curED<avgED) || (realContract<=contractBound) 
					)
				)
			{
				realContract++;
				contracted[candidates[curNode]]=true;
				// now copy its SCs in final SC list
				for(int i=candSCoffset[curNode]; i<candSCoffset[curNode+1]; i++)
				{
					srcSCfinal[totalNofSC]=srcSCall[i];
					trgSCfinal[totalNofSC]=trgSCall[i];
					wgtSCfinal[totalNofSC]=wgtSCall[i];
					totalNofSC++;
				}
			}
		}
		
				
		System.out.println("\n Will contract "+realContract+" nodes and creating "+totalNofSC+" SCs");
		
		// count surviving nodes and edges
		int nofDeletedEdges=0;
		newNofEdges=totalNofSC;
		for(int i=0; i<tempGraph.nofNodes(); i++)
			if (!contracted[i])
				newNofNodes++;
		assert(realContract==tempGraph.nofNodes()-newNofNodes);
		for(int j=0; j<tempGraph.nofEdges(); j++)
			if ((!contracted[tempGraph.edgeSource(j)])&&(!contracted[tempGraph.edgeTarget[j]]))
				newNofEdges++;
			else
				nofDeletedEdges++;
		assert((newNofEdges+nofDeletedEdges)==tempGraph.nofEdges()+totalNofSC);
		System.out.println("\n New Graph has "+newNofNodes+" nodes and "+newNofEdges+" edges having deleted "+nofDeletedEdges);
		
		// * assign all contracted nodes the new level in myCHgraph 
		// * add all created shortcuts to myCHgraph
		// * construct new tempGraph consisting of all surviving nodes and edges and shortcuts
		
		 
		RAMGraph newTempGraph=new RAMGraph();
		newTempGraph.createGraphSkeleton(newNofNodes, newNofEdges);
		int [] old2new=new int[tempGraph.nofNodes()];
		
		for(int i=0; i<tempGraph.nofNodes(); i++)
			if(!contracted[i])
			{
				old2new[i]=newTempGraph.addNode(tempGraph.xCoord(i), tempGraph.yCoord(i), tempGraph.altNodeID(i), 0);
			}
			else
			{
				old2new[i]=-1;
				assert(myCHGraph.level[tempGraph.altNodeID(i)]==Integer.MAX_VALUE);
				myCHGraph.level[tempGraph.altNodeID(i)]=newLevel;
			}
		
		// copy surviving edges to newTempGraph
		for(int j=0; j<tempGraph.nofEdges(); j++)
		{
			int curSrc=tempGraph.edgeSource(j), curTrg=tempGraph.edgeTarget(j), curWgt=tempGraph.edgeWeight(j);
			if ((!contracted[curSrc])&&(!contracted[curTrg]))	// copy edge to newTempGraph
			{
				newTempGraph.addEdge(old2new[curSrc], old2new[curTrg], curWgt, -2, -2);
			}
		}
		
		// now add SC edges to newTempGRaph as well as myCHGraph
		
		for(int j=0; j<totalNofSC; j++)
		{
			newTempGraph.addEdge(old2new[srcSCfinal[j]], old2new[trgSCfinal[j]], wgtSCfinal[j], -2,-2);
			myCHGraph.addEdge(tempGraph.altNodeID(srcSCfinal[j]), tempGraph.altNodeID(trgSCfinal[j]), wgtSCfinal[j], -2,-2);
		}
		
		newTempGraph.setupOffsets();
		
		tempGraph=newTempGraph.pruneGraph();
		// now create new tempGraph which consists of old graph (with edges between uncontracted nodes)
		// and all shortcuts created
		// 
		return tempGraph.nofNodes();
		
	}
	
	

}

