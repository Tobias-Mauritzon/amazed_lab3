package amazed.solver;

import amazed.maze.Maze;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <code>ForkJoinSolver</code> implements a solver for <code>Maze</code> objects
 * using a fork/join multi-thread depth-first search.
 * <p>
 * Instances of <code>ForkJoinSolver</code> should be run by a
 * <code>ForkJoinPool</code> object.
 */

public class ForkJoinSolver extends SequentialSolver {

	private final int branchStart;
	private final int player;
	private final static ConcurrentSkipListSet<Integer> concVisited = new ConcurrentSkipListSet<Integer>();
	private final static AtomicBoolean abort = new AtomicBoolean();
	
	/**
	 * Creates a solver that searches for a goal in the given maze. 
	 * Also creates a new player and reserves the first maze node to the created solver. 
	 * @param maze		The maze to be searched
	 */
	public ForkJoinSolver(Maze maze) {
		super(maze);
		this.branchStart = start;
		this.player = maze.newPlayer(start);
		
		concVisited.add(this.branchStart); //Reserves the start node to the root branch
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

	/**
	 * This constructor is used internally to create new branches
	 * @param maze	the maze to be searched
	 * @param forkAfter the number of steps a branch should take before branching
	 * @param branchStart the first node in the branch
	 * @param playerId the player that is created for this branch walk this branch
	 */
	private ForkJoinSolver(Maze maze, int forkAfter, int branchStart, int playerId) {
		super(maze);
		this.forkAfter = forkAfter;
		this.branchStart = branchStart;
		this.player = playerId;
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
	
	/**
	 * This method handles the search for a goal in a maze
	 * @return The path in the form of a list from branch start to goal if the goal has been found, otherwise null
	 */
	private List<Integer> parallelSearch() {
		//Stack<Integer> frontier = new Stack<>(); // Create new stack for the branch
		int count = 0; // Creates a new count variable for each branch
		
		frontier.push(branchStart); // Pushes the start of the branch to the stack
		
		// As long as the solver has more nodes to visit and no one has found the goal
		while (!frontier.empty() && !abort.get()) {
			
			int current = frontier.pop(); // Gets top of stack to explore 
			
			// If current is goal 
			// Move player to goal and tell other branches to stop looking 
			// return path from branchStart to goal
			if (maze.hasGoal(current)) {
				maze.move(player, current);
				abort.set(true);
				return pathFromTo(branchStart, current);
			}
			
			maze.move(player, current); // Move player to current
			
			int branchListSize = 0; // Number of nodes at the current end of the branch
			
			// For each 
			for (int nb : maze.neighbors(current)) {
				if (concVisited.add(nb)) {	
					frontier.push(nb);
					predecessor.put(nb, current);
					branchListSize++;
				}
			}

			if (branchListSize > 1 && count >= forkAfter) {
				ArrayList<ForkJoinSolver> branchList = new ArrayList<>();
				
				for (int i = 0; i < branchListSize - 1; i++) {
					int node = frontier.pop();
					ForkJoinSolver temp = new ForkJoinSolver(maze, forkAfter, node, maze.newPlayer(node));
					branchList.add(temp);
				}

				ForkJoinSolver mainBranch = new ForkJoinSolver(maze, forkAfter, frontier.pop(), player);
				for (ForkJoinSolver i :branchList) {
					i.fork();
				}
				
				List<Integer> retVal = mainBranch.compute();
				if (retVal != null) {
					return append(pathFromTo(branchStart, current), retVal);
				}
				
				for (ForkJoinSolver i :branchList) {
					retVal = i.join();
					if (retVal != null) {
						return append(pathFromTo(branchStart, current), retVal);
					}
				}
			} else count++;
		}
		return null;
	}
	
	/**
	 * Appends a given list to the end of another list
	 * @param list 			List to append
	 * @param branchList 	The list to append to the end of parameter list
	 * @return Appended 	list of type LinkedList<Integer>
	 */
	private List<Integer> append(List<Integer> list, List<Integer> branchList){
		return Stream.concat(list.stream(), branchList.stream()).collect(Collectors.toList());
	}
}
