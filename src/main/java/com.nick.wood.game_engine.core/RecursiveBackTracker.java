package com.nick.wood.game_engine.core;

import com.nick.wood.maths.objects.vector.Vec2i;

import java.util.ArrayList;
import java.util.Random;
import java.util.Stack;

public class RecursiveBackTracker {

	private final int width;
	private final int height;
	private final Stack<Cell> visitedStack;
	private final ArrayList<Cell> visited;
	private final Random random;
	private final Vec2i[] directions = new Vec2i[]{
			new Vec2i(0, -1), // up
			new Vec2i(0, 1), // down
			new Vec2i(-1, 0), // west
			new Vec2i(1, 0), // east
	};

	public RecursiveBackTracker(int width, int height) {

		this.width = width;
		this.height = height;

		this.visitedStack = new Stack<>();
		this.visited = new ArrayList<>();
		this.random = new Random();

		int startX = 0;
		int startY = 0;

		Vec2i startPosition = new Vec2i(startX, startY);
		Cell cell = new Cell(startPosition);

		// choose a square at random
		visited.add(cell);
		visitedStack.push(cell);

		RecBackTrackReturnValue recBackTrackReturnValue = new RecBackTrackReturnValue(cell, findNeighbours(cell));

		while (visited.size() != (width * height)) {

			recBackTrackReturnValue = checkSquare(recBackTrackReturnValue);


		}


	}

	private RecBackTrackReturnValue checkSquare(RecBackTrackReturnValue recBackTrackReturnValue) {

		int i = random.nextInt(recBackTrackReturnValue.getNeighbours().size());

		// get next cell
		Cell nextCell = recBackTrackReturnValue.getNeighbours().get(i);

		// add it to visited and stack
		visited.add(nextCell);
		visitedStack.push(nextCell);

		// find direction of travel and add onto path directions of start cell
		recBackTrackReturnValue.getCell().getPathDirections().add(nextCell.getPosition().subtract(recBackTrackReturnValue.getCell().getPosition()));

		// add path directions onto current cell
		nextCell.getPathDirections().add(recBackTrackReturnValue.getCell().getPosition().subtract(nextCell.getPosition()));

		// return if all visited
		if (visited.size() == (width * height)) {
			return null;
		}

		// find neighbours of the next cell
		ArrayList<Cell> nextNeighbours = findNeighbours(nextCell);

		// if neighbours is empty, find the last cell that has valid neighbours
		if (nextNeighbours.isEmpty()) {
			Cell lastFree = findLastFree(visitedStack);
			// and repeat function on it
			return new RecBackTrackReturnValue(lastFree, findNeighbours(lastFree));
		}
		// if it has neighbours, repeat function
		else {
			return new RecBackTrackReturnValue(nextCell, nextNeighbours);
		}

	}

	private ArrayList<Cell> findNeighbours(Cell startCell) {
		ArrayList<Cell> neighbours = new ArrayList<>();
		for (Vec2i direction : directions) {
			// get the new positions of the next square given 4 directions they could be
			Vec2i nextSquare = startCell.getPosition().add(direction);

			// check for valid square
			if (nextSquare.getX() >= 0 && nextSquare.getX() < width && nextSquare.getY() >= 0 && nextSquare.getY() < height) {

				// check if its already been visited
				// check if already visted
				boolean found = false;
				for (Cell cell : visited) {
					if (cell.getPosition().equals(nextSquare)) {
						found = true;
						break;
					}
				}
				if (!found) {

					neighbours.add(new Cell(nextSquare));

				}
			}
		}

		return neighbours;
	}

	private Cell findLastFree(Stack<Cell> visitedStack) {

		while (true) {
			if (visitedStack.isEmpty()) {
				System.out.println();
			}
			Cell pop = visitedStack.pop();
			ArrayList<Cell> neighbours = findNeighbours(pop);
			if (!neighbours.isEmpty()) {
				return pop;
			}
		}
	}

	public static void main(String[] args) {
		RecursiveBackTracker recursiveBackTracker = new RecursiveBackTracker(100, 100);

		System.out.println();
	}

	public ArrayList<Cell> getVisited() {
		return visited;
	}
}
