package amazed.solver;

import amazed.maze.Maze;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Stack;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <code>ForkJoinSolver</code> implements a solver for <code>Maze</code> objects
 * using a fork/join multi-thread depth-first search.
 * <p>
 * Instances of <code>ForkJoinSolver</code> should be run by a
 * <code>ForkJoinPool</code> object.
 */

public class ForkJoinSolver extends SequentialSolver {
	/**
	 * Creates a solver that searches in <code>maze</code> from the start node to a
	 * goal.
	 *
	 * @param maze the maze to be searched
	 */

	private final int branchStart;
	private final int player;
	private final static ConcurrentSkipListSet<Integer> concVisited = new ConcurrentSkipListSet<Integer>();
	private final static ConcurrentSkipListSet<Integer> taken = new ConcurrentSkipListSet<Integer>(); // las till
	private HashMap<Integer, Integer> predecessor; 
	private static AtomicBoolean abort = new AtomicBoolean();

	public ForkJoinSolver(Maze maze) {
		super(maze);
		this.branchStart = start;
		this.player = maze.newPlayer(start);
		
		this.predecessor = new HashMap<Integer, Integer>();
	}

	/**
	 * Creates a solver that searches in <code>maze</code> from the start node to a
	 * goal, forking after a given number of visited nodes.
	 *
	 * @param maze      the maze to be searched
	 * @param forkAfter the number of steps (visited nodes) after which a parallel
	 *                  task is forked; if <code>forkAfter &lt;= 0</code> the solver
	 *                  never forks new tasks
	 */
	public ForkJoinSolver(Maze maze, int forkAfter) {
		this(maze);
		this.forkAfter = forkAfter;
	}

	private ForkJoinSolver(Maze maze, int forkAfter, int branchStart, int playerId) {
		super(maze);
		this.forkAfter = forkAfter;
		this.branchStart = branchStart;
		this.player = playerId;
		
	}
	
	public ForkJoinSolver(Maze maze, int forkAfter, int branchStart, int playerId, HashMap<Integer, Integer> predecessor) {
		super(maze);
		this.forkAfter = forkAfter;
		this.branchStart = branchStart;
		this.player = playerId;
		this.predecessor = predecessor;
	}

	/**
	 * Searches for and returns the path, as a list of node identifiers, that goes
	 * from the start node to a goal node in the maze. If such a path cannot be
	 * found (because there are no goals, or all goals are unreacheable), the method
	 * returns <code>null</code>.
	 *
	 * @return the list of node identifiers from the start node to a goal node in
	 *         the maze; <code>null</code> if such a path cannot be found.
	 */
	@Override
	public List<Integer> compute() {
		return parallelSearch();
	}

	private List<Integer> parallelSearch() {
		Stack<Integer> frontier = new Stack<>();
		// start with start node
		frontier.push(branchStart);
		// as long as not all nodes have been processed
		while (!frontier.empty() && !abort.get()) {
			// get the new node to process
			int current = frontier.pop();
			// if current node has a goal
			if (maze.hasGoal(current)) {
				// move player to goal
				maze.move(player, current);
				// search finished: reconstruct and return path
				abort.set(true);
				return pathFromTo(start, current);
			}
			// if current node has not been visited yet
			if (!concVisited.contains(current)) {
				// move player to current node
				maze.move(player, current);
				// mark node as visited
				concVisited.add(current);
				taken.add(current); // las till
				// for every node nb adjacent to current

				for (int nb : maze.neighbors(current)) {
					// add nb to the nodes to be processed
					// frontier.push(nb);
					// if nb has not been already visited,
					// nb can be reached from current (i.e., current is nb's predecessor)
					if (!concVisited.contains(nb) && !taken.contains(nb)) { // vilkor las till
						frontier.push(nb);
						taken.add(nb); // las till
						predecessor.put(nb, current);
					}
				}

				if (frontier.size() > 1) {
					int branchListSize = frontier.size();
					ArrayList<ForkJoinSolver> branchList = new ArrayList<>();
					for (int i = 0; i < branchListSize - 1; i++) {
						int node = frontier.pop();
						HashMap<Integer, Integer> newPred = (HashMap<Integer, Integer>) predecessor.clone();
						ForkJoinSolver temp = new ForkJoinSolver(maze, 0, node, maze.newPlayer(node), newPred);
						branchList.add(temp);
					}
					
					HashMap<Integer, Integer> newPred = (HashMap<Integer, Integer>) predecessor.clone(); // Beöhvs här
					ForkJoinSolver mainBranch = new ForkJoinSolver(maze, 0, frontier.pop(), player, newPred);
				
					for (ForkJoinSolver i :branchList) {
						i.fork();
					}
					
					List<Integer> retVal = mainBranch.compute();
					if (retVal != null) {
						return retVal;
					}
					
					for (ForkJoinSolver i :branchList) {
						retVal = i.join();
						if (retVal != null) {
							return retVal;
						}
					}
				}
			}
		}
		return null;
	}

	protected List<Integer> pathFromTo(int from, int to) {
		List<Integer> path = new LinkedList<>();
		Integer current = to;
		while (current != from) {
			path.add(current);
			current = predecessor.get(current);
			if (current == null)
				return null;
		}
		path.add(from);
		Collections.reverse(path);
		return path;
	}
}
