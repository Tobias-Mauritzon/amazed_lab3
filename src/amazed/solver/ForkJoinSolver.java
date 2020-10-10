package amazed.solver;

import amazed.maze.Maze;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

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
	private final int playerStart;
	private final static ConcurrentSkipListSet<Integer> concVisited = new ConcurrentSkipListSet<Integer>();
	private final static ConcurrentHashMap<Integer, Integer> predecessor = new ConcurrentHashMap<Integer, Integer>();

	public ForkJoinSolver(Maze maze) {
		super(maze);
		this.branchStart = start;
		this.playerStart = -11;
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

	public ForkJoinSolver(Maze maze, int forkAfterint, int branchStart) {
		super(maze);
		this.forkAfter = forkAfter;
		this.branchStart = branchStart;
		this.playerStart = -11;
	}
	
	public ForkJoinSolver(Maze maze, int forkAfterint, int branchStart, int playerStart) {
		super(maze);
		this.forkAfter = forkAfter;
		this.branchStart = branchStart;
		this.playerStart = playerStart;
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
		// one player active on the maze at start
		int player;
		
		if(playerStart == -11) {
			player = maze.newPlayer(branchStart);
		}else {
			player = playerStart;
		}
		
		// start with start node
		frontier.push(branchStart);
		// as long as not all nodes have been processed
		while (!frontier.empty()) {
			// get the new node to process
			int current = frontier.pop();
			// if current node has a goal
			if (maze.hasGoal(current)) {
				// move player to goal
				maze.move(player, current);
				// search finished: reconstruct and return path
				return pathFromTo(start, current);
			}
			// if current node has not been visited yet
			if (!concVisited.contains(current)) {
				// move player to current node
				maze.move(player, current);
				// mark node as visited
				concVisited.add(current);
				// for every node nb adjacent to current

				for (int nb : maze.neighbors(current)) {
					// add nb to the nodes to be processed
					// frontier.push(nb);
					// if nb has not been already visited,
					// nb can be reached from current (i.e., current is nb's predecessor)
					if (!concVisited.contains(nb)) {
						frontier.push(nb);
						predecessor.put(nb, current);
					}
				}

				if (frontier.size() > 1) {
					ArrayList<ForkJoinSolver> branchList = new ArrayList<>();
					for (int i = frontier.size(); i > 1; i--) {
						ForkJoinSolver temp = new ForkJoinSolver(maze, 0, frontier.pop());
						temp.fork();
						branchList.add(temp);
					}
					
					ForkJoinSolver mainBranch = new ForkJoinSolver(maze, 0, frontier.pop(), player);
					List<Integer> retVal = mainBranch.compute();
					if (retVal != null) {
						return retVal;
					}

					for (ForkJoinSolver s : branchList) {
						List<Integer> branchVal = s.join();
						if (branchVal != null) {
							return branchVal;
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
