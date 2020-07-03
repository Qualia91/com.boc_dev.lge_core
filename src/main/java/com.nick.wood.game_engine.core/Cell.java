package com.nick.wood.game_engine.core;

import com.nick.wood.maths.objects.vector.Vec2i;

import java.util.ArrayList;

public class Cell {
	private final Vec2i position;
	private final ArrayList<Vec2i> pathDirections = new ArrayList<>();

	public Cell(Vec2i position) {
		this.position = position;
	}

	public Vec2i getPosition() {
		return position;
	}

	public ArrayList<Vec2i> getPathDirections() {
		return pathDirections;
	}
}
