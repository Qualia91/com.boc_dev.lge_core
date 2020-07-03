package com.nick.wood.game_engine.core;

import com.nick.wood.maths.noise.Perlin2Df;

import java.util.function.Function;

public class ProceduralGeneration {

	public ProceduralGeneration() {
	}

	/**
	 *  @param randomNumberArraySize
	 * @param size
	 * @param octaves
	 * @param lacunarity controls increase in frequency of octaves (2)
	 * @param persistence controls decrease in amplitude of octaves (0.5)
	 * @param segmentSize
	 **/
	public float[][] generateHeightMapChunk(int randomNumberArraySize,
	                                         int size,
	                                         int octaves,
	                                         double lacunarity,
	                                         double persistence,
	                                         int segmentSize,
	                                         int startX,
	                                         int startY,
	                                         int amplitudeScale,
	                                         Function<Double, Double> amplitudeScalingFunction) {


		float[][] grid = new float[size][size];

		for (int octave = 0; octave < octaves; octave++) {

			double frequency = Math.pow(lacunarity, octave);
			double amplitude = Math.pow(persistence, octave);
			int currentSegmentSize = (int) (segmentSize / frequency);

			Perlin2Df perlin2D = new Perlin2Df(randomNumberArraySize, currentSegmentSize);

			for (int i = startX; i < size + startX; i++) {
				for (int j = startY; j < size + startY; j++) {
					grid[i - startX][j - startY] += perlin2D.getPoint(i, j) * amplitudeScalingFunction.apply(amplitude * amplitudeScale);
				}
			}
		}

		return grid;


	}

	/**
	 * @param size
	 * @param persistence controls decrease in amplitude of octaves (0.5)
	 **/
	public float[][] generateHeightMapChunk(
	                                         int size,
	                                         double persistence,
	                                         int startX,
	                                         int startY,
	                                         Perlin2Df[] perlin2Ds,
	                                         int amplitudeScale,
	                                         Function<Double, Double> amplitudeScalingFunction) {

		float[][] grid = new float[size][size];

		for (int octave = 0; octave < perlin2Ds.length; octave++) {

			double amplitude = Math.pow(persistence, octave);

			for (int i = startX; i < size + startX; i++) {
				for (int j = startY; j < size + startY; j++) {
					grid[i - startX][j - startY] += perlin2Ds[octave].getPoint(Math.abs(i), Math.abs(j)) * amplitudeScalingFunction.apply(amplitude * amplitudeScale);
				}
			}
		}

		return grid;


	}

}
