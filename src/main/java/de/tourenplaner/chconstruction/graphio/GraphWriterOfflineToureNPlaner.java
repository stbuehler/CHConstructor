package de.tourenplaner.chconstruction.graphio;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import de.tourenplaner.chconstruction.graph.RAMGraph;

/**
 * Creates the ch graph file for OfflineToureNPlaner.
 *
 * REQUIRED INPUT: RAMGraph with nodes ordered by ascending CH level
 *
 * The data is split into 5 sections, each section is aligned at 4kbyte.
 * First comes the header, than 2 sections for the node data, and 2 for the edge data.
 *
 * Each node is assigned an id (for this file, no outside reference):
 * A nodeID has 32 bits, the higher 22 designate a block number, and the lower 10 bits an offset within the block
 * This means that each block can store up to 1024 nodes. Also the nodeIDs are (usually) not consecutive.
 *
 * The edgeIDs are consecutive and start at zero; each node has a consecutive list of incoming and outgoing edges,
 * each referenced by the first edgeID (and terminated by the next list of either outgoing edges of the same node
 * or incoming edges of the next node in the same block. each block has a terminating edge id for the last node).
 *
 * Both nodeIDs and edgeIDs are designed so that the file offset for associated data can be calculated very easily
 * (especially without doing extra IO). To make this work the associated data has fixed size.
 *
 * All integers are stored big-endian (network byte order).
 *
 * 1. section: header
 *    magic value/version:
 *    - magic1: 32 bit: 0x4348474F
 *    - magic2: 32 bit: 0x66665450
 *      => ASCII string: "CHGOffTP"
 *    - version: 32 bit: 0x1
 *
 *    grid base data:
 *    - baseLon, baseLat: 32 bit (integer in 1e-7 degree)
 *    - cellWidth, cellHeight: 32 bit (integer in 1e-7 degree)
 *    - gridWidth, gridHeight: 32 bit
 *
 *      The first gridWidth*gridHeight node blocks represent a grid for
 *        [baseLon..baseLon+gridWidth*cellWidth]x[baseLat..baseLat+gridHeight*cellHeight]
 *      All nodes in these blocks must be located in the grid cell.
 *      Each block can link (recursively) to another block which contains more nodes, which might
 *      be in the same cell. All nodes located in a grid cell must be reachable through this link.
 *      Different chain starts can end in a common chain. 
 *
 *    sizes and counts:
 *    - blockSize: max nodes in one block (usually 2^n - 1 for some n)
 *      This is used to calculate the fixed size that a block of nodes uses.
 *
 *    - blockCount: 32 bit (>= gridWidth*gridHeight)
 *
 *    - coreBlock: first core block
 *      there are no shortcuts stored for paths in the core graph; also nodes in the core graph have only
 *      outgoing edges.
 *
 *    - edgeCount: 32 bit
 *
 * 2. section: list of (header->blockCount) blocks with node geo data, each block needs 8 * (blockSize + 1) byte:
 *     - nextGridBlock: blockid (32 bits, less than 2^22), -1 for chain end
 *     - nodeCount: valid nodes in this block (32 bits, less than header->blockSize)
 *
 *     - list of (header->blockSize) GeoNodes:
 *       - longitude / latitude (each 32 bit, signed integer value in 1e-7 degree)
 *
 * 3. section: list of (header->blockCount) blocks with the edge ids for incoming/outgoing edges
 *     - 0: 32-bit (reserved - perhaps edge base offset??)
 *
 *     - list of (file->blockSize) EdgeNodes:
 *       - offset_edgesOutUp: 32 bit - edgeID of first outgoing edge
 *       - offset_edgesInDown: 32 bit - edgeID of first incoming edge
 *       for nodes *not* in the core the outgoing edges go to a node with higher (CH) level,
 *       and the incoming edges come from a node with higher (CH) level.
 *       for nodes in the core graph there are only outgoing edges (all on the same "infinite" CH level, not contracted)
 *
 *     - lastEdge: 32-bit: next edgeID after the edges of the last node
 *
 * 4. section: list of (header->edgeCount) edge graph data:
 *     - peer (NodeID): depending on type out/in the target/source of the edge
 *     - distance: 32-bit integer weight for dijkstra
 *
 * 5. section: list of (header->edgeCount) edge details:
 *     - euclidian distance: 32-bit integer in m
 *     - shortcut1, shortcut2: 32-bit: edgeIDs for shortcut nodes (both -1 if not a shortcut)
 *     - shortcut_node: 32-bit nodeID: contracted node (-1 if not a shortcut)
 *
 */
public class GraphWriterOfflineToureNPlaner implements GraphWriter {
	/**
	 * the file stores only the first (highest resolution) grid, but we sort nodes into different grids depending on their CH level
	 * nodes below level grid_sizes[i][0] are sorted in a grid_sizes[i][1]xgrid_sizes[i][1] grid
	 * nodes >= grid_sizes[#last][0] are sorted in the core graph
	 */
	private static final int[][] grid_sizes = { { 5, 256 }, { 10, 64 }, { 20, 32 }, { 40, 8 } };
	/** pick a block_size to use - must be <= 1024, and should be 2^n - 1 for some n */
	private static final int BLOCK_SIZE = 255;
	private RAMGraph g;

	/**
	 *  simple writer class that remembers the current file position
	 *  to align output at some page size
	 */
	private static class MyDataOutputStream implements Closeable {
		private DataOutputStream o;
		private long offset;

		public MyDataOutputStream(OutputStream os) {
			o = new DataOutputStream(os);
			offset = 0;
		}

		public void writeInt(int v) throws IOException {
			o.writeInt(v);
			offset += 4;
		}

		public void writeByte(int v) throws IOException {
			o.writeByte(v);
			offset += 1;
		}

		public void align(long pagesize) throws IOException {
			long padding = pagesize - (offset % pagesize);
			if (padding == pagesize) return;

			while (padding > 4) {
				this.writeInt(0);
				padding -= 4;
			}
			while (padding > 0) {
				this.writeByte(0);
				--padding;
			}
		}

		@Override
		public void close() throws IOException {
			o.close();
		}
	}

	public GraphWriterOfflineToureNPlaner() {
	}

	/** bounding box in 1e7 degrees */
	private int minLon, maxLon, minLat, maxLat;

	/** longitude in internal integer representation (*1e7) for a node */
	private int node_lon(int nodeID) {
		return (int)(1e7*g.getLon(nodeID));
	}
	/** latitude in internal integer representation (*1e7) for a node */
	private int node_lat(int nodeID) {
		return (int)(1e7*g.getLat(nodeID));
	}

	/** calculate bounding box for grid */
	public void DO_CalcBounds() {
		minLon = minLat = Integer.MAX_VALUE;
		maxLon = maxLat = 0;
		for (int i = 0, l = g.nofNodes(); i < l; ++i) {
			minLon = Math.min(minLon, node_lon(i));
			maxLon = Math.max(maxLon, node_lon(i));
			minLat = Math.min(minLat, node_lat(i));
			maxLat = Math.max(maxLat, node_lat(i));
		}
		System.out.println("Size: " + (maxLon - minLon) + " x " + (maxLat - minLat));
	}

	private int[] block_data; /* BLOCK_VALUES ints for each block: x, y, level, next blockID, node count in this block */
	private int[] blocks_nodes; /* for each block BLOCK_SIZE node indices in the original graph */
	private int blocks_used;
	private int blocks_max; /* how many blocks can be stored in block_data/block_nodes */
	private final int BLOCK_VALUES = 5;
	private final int BV_BASEX = 0, BV_BASEY = 1, BV_LEVEL = 2, BV_NEXT = 3, BV_COUNT = 4;

	private int createBlock(int base_x, int base_y, int level) {
		int ndx = blocks_used++;
		if (blocks_used > blocks_max) {
			/* resize block_data and block_nodes array */
			blocks_max = Math.max(blocks_max, grid_sizes[0][1] * grid_sizes[0][1]) * 2;
			int[] tmp;

			tmp = new int[blocks_max * BLOCK_VALUES];
			if (block_data != null) System.arraycopy(block_data, 0, tmp, 0, block_data.length);
			block_data = tmp;

			tmp = new int[blocks_max * BLOCK_SIZE];
			if (blocks_nodes != null) {
				System.arraycopy(blocks_nodes, 0, tmp, 0, blocks_nodes.length);
				Arrays.fill(tmp, blocks_nodes.length, tmp.length, -1);
			} else {
				Arrays.fill(tmp, -1);
			}
			blocks_nodes = tmp;

			System.gc();
		}

		block_data[ndx*BLOCK_VALUES + BV_BASEX] = base_x;
		block_data[ndx*BLOCK_VALUES + BV_BASEY] = base_y;
		block_data[ndx*BLOCK_VALUES + BV_LEVEL] = level;
		block_data[ndx*BLOCK_VALUES + BV_NEXT] = -1; /* next */
		block_data[ndx*BLOCK_VALUES + BV_COUNT] = 0; /* count */

		return ndx;
	}

	/**
	 * for all grid cells in all grids store the current block to insert nodes into (or -1)
	 * starting with the first cell in the first row of the first grid
	 */
	private int[] cell_blocks;
	private int base_cell_x, base_cell_y;
	private int base_cell_width, base_cell_height;
	private int core_block_start;

	private int getGridX(int level, int x) {
		final long basex = (x - base_cell_x) / base_cell_width;
		return (int) ((basex * grid_sizes[level][1]) / grid_sizes[0][1]);
	}
	private int getGridY(int level, int y) {
		final long basey = (y - base_cell_y) / base_cell_height;
		return (int) ((basey * grid_sizes[level][1]) / grid_sizes[0][1]);
	}
	private int getLocalGridOffset(int level, int x, int y) {
		return getGridY(level, y) * grid_sizes[level][1] + getGridX(level, x);
	}
	private int getBaseGridOffset(int x, int y) {
		return getLocalGridOffset(0, x, y);
	}
	private int getGridOffset(int level, int x, int y) {
		int baseNdx = 0;
		for (int i = 0; i < level; ++i) baseNdx += grid_sizes[i][1]*grid_sizes[i][1];
		return baseNdx + getLocalGridOffset(level, x, y);
	}
	private int getGridBaseX(int level, int cellX) {
		final int baseCellX = cellX * (grid_sizes[0][1] / grid_sizes[level][1]);
		return base_cell_x + base_cell_width * baseCellX;
	}
	private int getGridBaseY(int level, int cellY) {
		final int baseCellY = cellY * (grid_sizes[0][1] / grid_sizes[level][1]);
		return base_cell_y + base_cell_height * baseCellY;
	}

	private void DO_PrepareCellBlocks() {
		base_cell_x = minLon - 1;
		base_cell_y = minLat - 1;

		int cell_count = 0;
		for (int i = 0; i < grid_sizes.length; ++i) cell_count += grid_sizes[i][1]*grid_sizes[i][1];

		int n = grid_sizes[0][1];
		base_cell_width = (maxLon - minLon) / n + 1;
		base_cell_height = (maxLat - minLat) / n + 1;
		System.out.println("Base cell size: " + base_cell_width + " x " + base_cell_height);

		cell_blocks = new int[cell_count];
		for (int i = 0; i < cell_count; ++i) cell_blocks[i] = -1;

		blocks_used = blocks_max = 0;
		block_data = blocks_nodes = null;
		core_block_start = -1;

		// base grid must always be allocated in a fixed order
		for (int i = 0, x = 0; x < n; ++x) {
			for (int y = 0; y < n; ++y, ++i) {
				int ndx = createBlock(base_cell_x + x * base_cell_width, base_cell_y + y * base_cell_height, 0);
				cell_blocks[i] = ndx;
				assert(i == ndx);
			}
		}
	}

	/** follow chain for a base cell, so we can link a new block to it */
	private int findBaseCellLastBlock(int x, int y) {
		int block = cell_blocks[getBaseGridOffset(x, y)];
		assert -1 != block;
		int t;
		while (-1 != (t = block_data[block*BLOCK_VALUES + BV_NEXT])) {
			block = t;
		}
		return block;
	}

	/** for each index in the original graph store the assigned nodeID */
	private int[] nodeBlockIDs;

	/** follow chain to the last block; chain must stay in the same grid level */
	private int sameLevelLastBlock(int block) {
		if (-1 == block) return block;
		int t;
		while (-1 != (t = block_data[block*BLOCK_VALUES + BV_NEXT])) {
			if (!(block_data[t*BLOCK_VALUES+BV_BASEX] == block_data[block*BLOCK_VALUES+BV_BASEX]
					&& block_data[t*BLOCK_VALUES+BV_BASEY] == block_data[block*BLOCK_VALUES+BV_BASEY]
					&& block_data[t*BLOCK_VALUES+BV_LEVEL] == block_data[block*BLOCK_VALUES+BV_LEVEL])) {
				throw new RuntimeException("block chain is not in the same grid level");
			}
			block = t;
		}
		return block;
	}

	/** add new block to end of a chain (same base point/level) */
	private int blockExtendBlock(int block) {
		assert(-1 == block_data[block*BLOCK_VALUES+BV_NEXT]);
		int t = createBlock(block_data[block*BLOCK_VALUES+BV_BASEX],
				block_data[block*BLOCK_VALUES+BV_BASEY],
				block_data[block*BLOCK_VALUES+BV_LEVEL]);
		block_data[block*BLOCK_VALUES+BV_NEXT] = t;
		return t;
	}

	/** add node to a block chain (extend chain if necessary) */
	private int blockAddNode(int node, int block) {
		block = sameLevelLastBlock(block);
		if (block_data[block*BLOCK_VALUES + BV_COUNT] >= BLOCK_SIZE) {
			block = blockExtendBlock(block);
		}
		int bndx = block_data[block*BLOCK_VALUES + BV_COUNT]++;
		assert(-1 == blocks_nodes[block*BLOCK_SIZE + bndx]);
		blocks_nodes[block*BLOCK_SIZE + bndx] = node;
		return (block << 10) + bndx;
	}

	private int curLevel; /** current grid level. used to print stats and verify ascending CH Level */
	private int curLevelNodes; /** nodes on current level for stats */

	/** sort a node into the grid. must be called in ascending rank (CH Level) order
	 * @param node index in original graph
	 * @param x internal longitude (in 1e-7 degree)
	 * @param y internal latitude (in 1e-7 degree)
	 * @param rank CH level
	 */
	private int blocksAddNode(int node, int x, int y, int rank) {
		for (int i = 0; i < grid_sizes.length; ++i) {
			if (grid_sizes[i][0] > rank) {
				// print stats if we hit this level the first time
				if (curLevel < i) {
					System.out.println("After Level " + curLevel + ": Blocks in use: " + blocks_used + " (min: " + ((BLOCK_SIZE+1+curLevelNodes)/BLOCK_SIZE) + ")");
					curLevel = i;
					curLevelNodes = 0;
				}
				if (curLevel != i) {
					throw new RuntimeException("Nodes not in CH level ascending order");
				}
				curLevelNodes++;
				final int cellNdx = getGridOffset(i, x, y);
				int block = cell_blocks[cellNdx];
				if (-1 == block) {
					assert i > 0;
					block = createBlock(getGridBaseX(i, getGridX(i,  x)), getGridBaseY(i, getGridY(i, x)), i);
					cell_blocks[cellNdx] = block;
				}
				if (i > 0) {
					// above base grid, make sure we have a link up
					int old = findBaseCellLastBlock(x, y);
					assert(-1 != old);
					if (old < block) {
						// no link yet
						assert(-1 == block_data[old*BLOCK_VALUES + BV_NEXT]);
						block_data[old*BLOCK_VALUES + BV_NEXT] = block;
					} else {
						// current chain should end in the link
						assert(old == sameLevelLastBlock(block));
					}
				}
				return blockAddNode(node, block);
			}
		}
		// got a core node!

		// print stats if we hit this level the first time
		if (curLevel < Integer.MAX_VALUE) {
			System.out.println("After Level " + curLevel + ": Blocks in use: " + blocks_used + " (min: " + ((BLOCK_SIZE+1+curLevelNodes)/BLOCK_SIZE) + ")");
			curLevel = Integer.MAX_VALUE;
		}

		if (-1 == core_block_start) core_block_start = createBlock(base_cell_x,base_cell_y,Integer.MAX_VALUE);

		// above base grid, make sure we have a link up
		int old = findBaseCellLastBlock(x, y);
		assert(-1 != old);
		if (old < core_block_start) {
			// no link yet
			assert(-1 == block_data[old*BLOCK_VALUES + BV_NEXT]);
			block_data[old*BLOCK_VALUES + BV_NEXT] = core_block_start;
		} else {
			// current chain should end in the link
			assert(old == sameLevelLastBlock(core_block_start));
		}

		return blockAddNode(node, core_block_start);
	}

	/** length of a block chain starting at block */
	private int blockChainLength(int block) {
		int len = 0;
		while (-1 != block) {
			block = block_data[block*BLOCK_VALUES + BV_NEXT];
			++len;
		}
		return len;
	}

	/** length of a block chain at the same level starting at block */
	private int blockLevelChainLength(int block) {
		int len = 0;
		if (-1 == block) return 0;
		int lvl = block_data[block*BLOCK_VALUES + BV_LEVEL];

		do {
			++len;
			block = block_data[block*BLOCK_VALUES + BV_NEXT];
		} while (-1 != block && lvl == block_data[block*BLOCK_VALUES + BV_LEVEL]);
		return len;
	}

	/** sort all nodes into grid and their blocks */
	private void DO_FillBlocks() {
		nodeBlockIDs = new int[g.nofNodes()];
		for (int i = 0, l = g.nofNodes(); i < l; ++i) {
			nodeBlockIDs[i] = blocksAddNode(i, node_lon(i), node_lat(i), g.getLevel(i));
			checkNodeID(nodeBlockIDs[i]);
		}
		// stats
		System.out.println("Blocks in use: " + blocks_used + " (min: " + ((BLOCK_SIZE+1+g.nofNodes())/BLOCK_SIZE) + ")");
		{
			for (int level = 0, i = 0; level < grid_sizes.length; ++level) {
				int maxChain = 1, blocks = 0;
				for (int k = 0, l = grid_sizes[level][1]*grid_sizes[level][1]; k < l; ++k, ++i) {
					maxChain = Math.max(maxChain, blockChainLength(cell_blocks[i]));
					blocks += blockLevelChainLength(cell_blocks[i]);
				}
				System.out.println("Max chain length for level " + level + ": " + maxChain + ", total blocks: " + blocks);
			}
		}
		System.out.println("Core blocks: " + blockChainLength(core_block_start));
	}

	private int[] nodeFirstOutEdgeID, nodeFirstInEdgeID, nodeEndEdgeID; /** index from original graph */

	/** indices mapping in both directions */
	private int[] edges; /** (out file) edgeID -> original graph id */
	private int[] edgesReverse; /** original graph ID -> (out file) edgeID */

	private int use_edges; /** number of edges we want to store; we drop shortcuts in the core graph */
	private void DO_CountAndSortEdges() {
		use_edges = 0;
		final int coreRank = grid_sizes[grid_sizes.length-1][0];
		nodeFirstOutEdgeID = new int[g.nofNodes()];
		nodeFirstInEdgeID = new int[g.nofNodes()];
		nodeEndEdgeID = new int[g.nofNodes()];

		// count edges for each node
		for (int i = 0, l = g.nofEdges(); i < l; ++i) {
			int s = g.getSource(i), t = g.getTarget(i);
			int srank = g.getLevel(s), trank = g.getLevel(t);
			assert(srank != trank);
			if (srank >= coreRank && trank >= coreRank) {
				final int sc1 = g.getSkippedA(i);
				if (-1 == sc1 || g.getLevel(g.getTarget(sc1)) < coreRank) {
					// don't store core shortcuts
					// store core edges always in the source
					++nodeFirstOutEdgeID[s];
					++use_edges;
				}
			} else if (srank < trank) {
				++nodeFirstOutEdgeID[s];
				++use_edges;
			} else {
				++nodeFirstInEdgeID[t];
				++use_edges;
			}
		}

		// store start offset for edge mapping below
		final int[] nextOutEdge = new int[g.nofNodes()];
		final int[] nextInEdge = new int[g.nofNodes()];

		// calculate outgoing and incoming first edgeID for each node 
		int nextEdgeID = 0;
		for (int i = 0; i < blocks_used; ++i) {
			int count = block_data[i*BLOCK_VALUES + BV_COUNT];

			for (int j = 0, off = i * BLOCK_SIZE; j < BLOCK_SIZE; ++j, ++off) {
				int n = blocks_nodes[off];
				assert((-1 != n) == (j < count));
				if (-1 != n) {
					int currentEdgeID;

					currentEdgeID = nextEdgeID;
					nextEdgeID += nodeFirstOutEdgeID[n];
					nextOutEdge[n] = nodeFirstOutEdgeID[n] = currentEdgeID;

					currentEdgeID = nextEdgeID;
					nextEdgeID += nodeFirstInEdgeID[n];
					nextInEdge[n] = nodeFirstInEdgeID[n] = currentEdgeID;
					nodeEndEdgeID[n] = nextEdgeID;
				}
			}
		}

		edges = new int[use_edges];
		edgesReverse = new int[g.nofEdges()];

		for (int i = 0, l = g.nofEdges(); i < l; ++i) {
			int s = g.getSource(i), t = g.getTarget(i);
			int srank = g.getLevel(s), trank = g.getLevel(t);
			assert(srank != trank);
			if (srank >= coreRank && trank >= coreRank) {
				final int sc1 = g.getSkippedA(i);
				if (-1 == sc1 || g.getLevel(g.getTarget(sc1)) < coreRank) {
					// don't store core shortcuts
					// store core edges always in the source
					int k = nextOutEdge[s]++;
					edgesReverse[i] = k;
					edges[k] = i;
				} else {
					edgesReverse[i] = -1;
				}
			} else if (srank < trank) {
				int k = nextOutEdge[s]++;
				edgesReverse[i] = k; 
				edges[k] = i;
			} else {
				int k = nextInEdge[t]++;
				edgesReverse[i] = k; 
				edges[k] = i;
			}
		}
	}

	/* 1. section */
	private void writeHeader(MyDataOutputStream o) throws IOException {
		// magic ("CHGOffTP") + version (1)
		o.writeInt(0x4348474F);
		o.writeInt(0x66665450);
		o.writeInt(1);

		o.writeInt(base_cell_x);
		o.writeInt(base_cell_y);
		o.writeInt(base_cell_width);
		o.writeInt(base_cell_height);
		o.writeInt(grid_sizes[0][1]);
		o.writeInt(grid_sizes[0][1]);
		o.writeInt(BLOCK_SIZE);
		o.writeInt(blocks_used);
		o.writeInt(core_block_start);
		o.writeInt(use_edges);
	}

	/* 2. section */
	private void writeNodeGeoBlocks(MyDataOutputStream o) throws IOException {
		for (int i = 0; i < blocks_used; ++i) {
			int nextBlock = block_data[i*BLOCK_VALUES + BV_NEXT];
			o.writeInt(nextBlock);
			int count = block_data[i*BLOCK_VALUES + BV_COUNT];
			assert(count <= BLOCK_SIZE);
			o.writeInt(count);

			for (int j = 0, off = i * BLOCK_SIZE; j < BLOCK_SIZE; ++j, ++off) {
				int n = blocks_nodes[off];
				assert((-1 != n) == (j < count));
				if (-1 != n) {
					o.writeInt(node_lon(n));
					o.writeInt(node_lat(n));
				} else {
					o.writeInt(0); o.writeInt(0);
				}
			}
		}
	}

	/* 3. section */
	private void writeNodeEgesBlocks(MyDataOutputStream o) throws IOException {
		int currentEndEdgeID = 0;
		for (int i = 0; i < blocks_used; ++i) {
			int count = block_data[i*BLOCK_VALUES + BV_COUNT];

			o.writeInt(0);

			for (int j = 0, off = i * BLOCK_SIZE; j < BLOCK_SIZE; ++j, ++off) {
				int n = blocks_nodes[off];
				assert((-1 != n) == (j < count));
				if (-1 != n) {
					o.writeInt(nodeFirstOutEdgeID[n]);
					o.writeInt(nodeFirstInEdgeID[n]);
					currentEndEdgeID = nodeEndEdgeID[n];
				} else {
					o.writeInt(currentEndEdgeID);
					o.writeInt(currentEndEdgeID);
				}
			}

			o.writeInt(currentEndEdgeID);
		}
	}

	/* 4. section */
	private void writeEdgesBlock(MyDataOutputStream o) throws IOException {
		final int coreRank = grid_sizes[grid_sizes.length-1][0];

		for (int i = 0, l = use_edges; i < l; ++i) {
			int e = edges[i];
			int s = g.getSource(e), t = g.getTarget(e);
			int srank = g.getLevel(s), trank = g.getLevel(t);
			assert(srank != trank);
			/* either store target in "CH up" direction or target in the core */
			if (srank < trank || trank >= coreRank) {
				o.writeInt(nodeBlockIDs[t]);
			} else {
				o.writeInt(nodeBlockIDs[s]);
			}
			o.writeInt(g.getWeight(e));
		}
	}

	/* 5. section */
	private void writeEdgesDetailsBlock(MyDataOutputStream o) throws IOException {
		for (int i = 0, l = use_edges; i < l; ++i) {
			int e = edges[i];
			int sc1 = g.getSkippedA(e), sc2 = g.getSkippedB(e); 
			o.writeInt(g.getEuclidianLength(e));
			assert((-1 == sc1) == (-1 == sc2));
			if (-1 == sc1) {
				o.writeInt(-1);
				o.writeInt(-1);
				o.writeInt(-1);
			} else {
				assert -1 != edgesReverse[sc1];
				assert -1 != edgesReverse[sc2];
				o.writeInt(edgesReverse[sc1]);
				o.writeInt(edgesReverse[sc2]);
				assert(g.getTarget(sc1) == g.getSource(sc2));
				o.writeInt(nodeBlockIDs[g.getTarget(sc1)]);
			}
		}
	}

	private void DO_Write(OutputStream os) throws IOException {
		MyDataOutputStream o = new MyDataOutputStream(new BufferedOutputStream(os));

		writeHeader(o);
		o.align(4096);

		writeNodeGeoBlocks(o);
		o.align(4096);

		writeNodeEgesBlocks(o);
		o.align(4096);

		writeEdgesBlock(o);
		o.align(4096);

		writeEdgesDetailsBlock(o);

		o.close();
	}

	private void checkNodeID(int nodeid) {
		if ( (nodeid >>> 10) >= blocks_used ) throw new java.lang.AssertionError();
		if ( (nodeid & 1023) >= BLOCK_SIZE ) throw new java.lang.AssertionError();
	}

	@Override
	public void writeRAMGraph(OutputStream out, RAMGraph ramGraph) throws IOException {
		this.g = ramGraph;
		System.out.println("Writing Offline ToureNPlaner Graph: Nodes: " + g.nofNodes() + ", Edges: " + g.nofEdges());
		DO_CalcBounds();
		DO_PrepareCellBlocks();
		DO_FillBlocks();
		DO_CountAndSortEdges();

		DO_Write(out);
	}
}
