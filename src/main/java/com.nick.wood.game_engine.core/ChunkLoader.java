package com.nick.wood.game_engine.core;

import com.nick.wood.game_engine.event_bus.event_data.GameObjectEventData;
import com.nick.wood.game_engine.event_bus.event_types.GameObjectEventType;
import com.nick.wood.game_engine.event_bus.events.GameObjectEvent;
import com.nick.wood.game_engine.event_bus.interfaces.Bus;
import com.nick.wood.game_engine.model.game_objects.*;
import com.nick.wood.maths.noise.Perlin2Df;
import com.nick.wood.maths.objects.srt.Transform;
import com.nick.wood.maths.objects.srt.TransformBuilder;
import com.nick.wood.maths.objects.vector.Vec2i;
import com.nick.wood.maths.objects.vector.Vec3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkLoader {

	private final GameObject parent;

	private final int chunkSize = 50;
	private final int segmentSize = 100;
	private final ArrayList<Vec2i> loadedChunkIndices = new ArrayList<>();
	private final ConcurrentHashMap<Vec2i, GameObject> chunkIndexSceneGraphHashMap = new ConcurrentHashMap<>();
	private final Perlin2Df[] perlin2Ds;
	private final int cellSpace = 1000;

	private final int loadingClippingDistance;
	private final int loadingClippingDistance2;
	private final int visualClippingDistance2;
	private final ArrayList<TerrainTextureGameObject> terrainTextureGameObjects;
	private final Bus bus;

	public ChunkLoader(GameObject parent, int octaves, int lacunarity, int visualRange, Bus bus) {

		this.bus = bus;
		this.loadingClippingDistance = visualRange;
		this.visualClippingDistance2 = (visualRange * visualRange) + 1;
		this.loadingClippingDistance2 = visualClippingDistance2 * 2;

		perlin2Ds = new Perlin2Df[octaves];
		for (int i = 0; i < octaves; i++) {
			double frequency = Math.pow(lacunarity, i);
			int currentSegmentSize = (int) (segmentSize / frequency);
			perlin2Ds[i] = new Perlin2Df(10000, currentSegmentSize);
		}

		this.parent = parent;

		this.terrainTextureGameObjects = new ArrayList<>();

		terrainTextureGameObjects.add(new TerrainTextureGameObject(
				0,
				500,
				"/textures/sand.jpg",
				"/normalMaps/sandNormalMap.jpg"
		));

		terrainTextureGameObjects.add(new TerrainTextureGameObject(
				500,
				2500,
				"/textures/terrain2.jpg",
				"/normalMaps/grassNormal.jpg"
		));

		terrainTextureGameObjects.add(new TerrainTextureGameObject(
				7000,
				1000,
				"/textures/snow.jpg",
				"/normalMaps/large.jpg"
		));
	}

	public void loadChunk(Vec3f currentPlayerPosition) {

		// get the index of the player position
		int xIndex = (int) (currentPlayerPosition.getX() / (double) (chunkSize * cellSpace));
		int yIndex = (int) (currentPlayerPosition.getY() / (double) (chunkSize * cellSpace));

		Vec2i playerChunk = new Vec2i(xIndex, yIndex);

		// use this position to create the tiles all around the player
		// load all 16 chunks around it
		for (int x = xIndex - loadingClippingDistance; x <= xIndex + loadingClippingDistance; x++) {
			for (int y = yIndex - loadingClippingDistance; y <= yIndex + loadingClippingDistance; y++) {

				Vec2i chunkIndex = new Vec2i(x, y);

				// see if the chunk hasn't already been loaded
				if (!loadedChunkIndices.contains(chunkIndex)) {
					// add chunk to new list
					// and load it
					GameObject gameObject = createChunk(chunkIndex);
					chunkIndexSceneGraphHashMap.put(chunkIndex, gameObject);
					loadedChunkIndices.add(chunkIndex);

				}
			}
		}

		// see if the chunk hasn't already been loaded
		Iterator<Vec2i> iterator = loadedChunkIndices.iterator();
		while (iterator.hasNext()) {
			Vec2i next = iterator.next();
			int dist = next.distance2AwayFrom(playerChunk);
			// if chunk is within visual range, set render to true
			if (dist < visualClippingDistance2) {
				bus.dispatch(new GameObjectEvent(
						new GameObjectEventData(chunkIndexSceneGraphHashMap.get(next).getGameObjectData().getUuid()),
						GameObjectEventType.SHOW_ALL
				));
			}
			else if (dist < loadingClippingDistance2) {
				bus.dispatch(new GameObjectEvent(
						new GameObjectEventData(chunkIndexSceneGraphHashMap.get(next).getGameObjectData().getUuid()),
						GameObjectEventType.HIDE_ALL
				));
			}
			else {
				destroyChunk(next);
				iterator.remove();
			}
		}


	}

	private void destroyChunk(Vec2i chunkIndex) {
		bus.dispatch(new GameObjectEvent(
				new GameObjectEventData(chunkIndexSceneGraphHashMap.get(chunkIndex).getGameObjectData().getUuid()),
				GameObjectEventType.REMOVE
		));
		chunkIndexSceneGraphHashMap.remove(chunkIndex);
	}

	private GameObject createChunk(Vec2i chunkIndex) {

		ProceduralGeneration proceduralGeneration = new ProceduralGeneration();
		float[][] grid = proceduralGeneration.generateHeightMapChunk(
				chunkSize + 1,
				0.7,
				chunkIndex.getX() * chunkSize,
				chunkIndex.getY() * chunkSize,
				perlin2Ds,
				30,
				amp -> amp * amp * amp
		);


		Transform transform = new TransformBuilder()
				.setPosition(new Vec3f(chunkIndex.getX() * chunkSize * cellSpace, chunkIndex.getY() * chunkSize * cellSpace, 0)).build();

		TransformObject transformObject = new TransformObject(transform);
		transformObject.getGameObjectData().hideAll();

		TerrainChunkObject terrainChunkObject = new TerrainChunkObject(
				chunkIndex.toString(),
				grid,
				terrainTextureGameObjects,
				cellSpace
		);
		transformObject.getGameObjectData().attachGameObjectNode(terrainChunkObject);

		return transformObject;
	}

}
