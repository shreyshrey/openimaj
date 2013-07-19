package org.openimaj.image.feature.dense.gradient.binning;

import org.openimaj.image.analysis.algorithm.histogram.GradientOrientationHistogramExtractor;
import org.openimaj.image.analysis.algorithm.histogram.WindowedHistogramExtractor;
import org.openimaj.image.analysis.algorithm.histogram.binning.SpatialBinningStrategy;
import org.openimaj.image.feature.dense.gradient.binning.FixedHOGStrategy.BlockNormalisation;
import org.openimaj.math.geometry.shape.Rectangle;
import org.openimaj.math.statistics.distribution.Histogram;

/**
 * A {@link SpatialBinningStrategy} very much like the {@link FixedHOGStrategy},
 * but with flexibly sized cells that grow/shrink such that the number of cells
 * in any given window is constant. Coupled with a
 * {@link GradientOrientationHistogramExtractor}, this provides a way to
 * efficiently extract the HOG descriptor of any rectangular window.
 * 
 * @author Jonathon Hare (jsh2@ecs.soton.ac.uk)
 */
public class FlexibleHOGStrategy implements SpatialBinningStrategy {
	int numCellsX = 8;
	int numCellsY = 16;
	int cellsPerBlockX = 2;
	int cellsPerBlockY = 2;
	BlockNormalisation norm = BlockNormalisation.L2;

	private int numBlocksX;
	private int numBlocksY;
	private Histogram[][] blocks;
	private Histogram[][] cells;
	private int blockLength;
	private int blockArea;
	private int blockStepX;
	private int blockStepY;

	/**
	 * Construct with square cells of the given size (in pixels). Square blocks
	 * are constructed from the given number of cells in each dimension. Blocks
	 * overlap, and shift by 1 cell (i.e. overlap is cellsPerBlock - 1).
	 * {@link BlockNormalisation#L2} is used for block normalisation.
	 * 
	 * @param numCellsX
	 *            the number of cells per window in the x direction
	 * @param numCellsY
	 *            the number of cells per window in the y direction
	 * @param cellsPerBlock
	 *            the number of cells per block
	 */
	public FlexibleHOGStrategy(int numCellsX, int numCellsY, int cellsPerBlock) {
		this(numCellsX, numCellsY, cellsPerBlock, 1, BlockNormalisation.L2);
	}

	/**
	 * Construct with square cells of the given size (in pixels). Square blocks
	 * are constructed from the given number of cells in each dimension. Blocks
	 * overlap, and shift by 1 cell (i.e. overlap is cellsPerBlock - 1).
	 * 
	 * @param numCellsX
	 *            the number of cells per window in the x direction
	 * @param numCellsY
	 *            the number of cells per window in the y direction
	 * @param cellsPerBlock
	 *            the number of cells per block
	 * @param norm
	 *            the normalisation scheme
	 */
	public FlexibleHOGStrategy(int numCellsX, int numCellsY, int cellsPerBlock, BlockNormalisation norm) {
		this(numCellsX, numCellsY, cellsPerBlock, 1, norm);
	}

	/**
	 * Construct with square cells of the given size (in pixels). Square blocks
	 * are constructed from the given number of cells in each dimension.
	 * 
	 * @param numCellsX
	 *            the number of cells per window in the x direction
	 * @param numCellsY
	 *            the number of cells per window in the y direction
	 * @param cellsPerBlock
	 *            the number of cells per block
	 * @param blockStep
	 *            the amount to shift each block in terms of cells
	 * @param norm
	 *            the normalisation scheme
	 */
	public FlexibleHOGStrategy(int numCellsX, int numCellsY, int cellsPerBlock, int blockStep, BlockNormalisation norm) {
		this(numCellsX, numCellsY, cellsPerBlock, cellsPerBlock, blockStep, blockStep, norm);
	}

	/**
	 * Construct with square cells of the given size (in pixels). Square blocks
	 * are constructed from the given number of cells in each dimension.
	 * 
	 * @param numCellsX
	 *            the number of cells per window in the x direction
	 * @param numCellsY
	 *            the number of cells per window in the y direction
	 * @param cellsPerBlockX
	 *            the number of cells per block in the x direction
	 * @param cellsPerBlockY
	 *            the number of cells per block in the y direction
	 * @param blockStepX
	 *            the amount to shift each block in terms of cells in the x
	 *            direction
	 * @param blockStepY
	 *            the amount to shift each block in terms of cells in the y
	 *            direction
	 * @param norm
	 *            the normalisation scheme
	 */
	public FlexibleHOGStrategy(int numCellsX, int numCellsY, int cellsPerBlockX, int cellsPerBlockY,
			int blockStepX, int blockStepY, BlockNormalisation norm)
	{
		super();
		this.numCellsX = numCellsX;
		this.numCellsY = numCellsY;
		this.cellsPerBlockX = cellsPerBlockX;
		this.cellsPerBlockY = cellsPerBlockY;
		this.norm = norm;
		this.blockStepX = blockStepX;
		this.blockStepY = blockStepY;

		cells = new Histogram[numCellsY][numCellsX];

		numBlocksX = (cells[0].length - cellsPerBlockX) / blockStepX;
		numBlocksY = (cells.length - cellsPerBlockY) / blockStepY;
		blocks = new Histogram[numBlocksY][numBlocksX];
	}

	@Override
	public Histogram extract(WindowedHistogramExtractor binnedData, Rectangle region, Histogram output) {
		if (cells[0][0] == null || cells[0][0].values.length != binnedData.getNumBins()) {
			for (int j = 0; j < numCellsY; j++)
				for (int i = 0; i < numCellsX; i++)
					cells[j][i] = new Histogram(binnedData.getNumBins());

			for (int j = 0; j < numBlocksY; j++)
				for (int i = 0; i < numBlocksX; i++)
					blocks[j][i] = new Histogram(binnedData.getNumBins() * cellsPerBlockX * cellsPerBlockY);

			blockLength = blocks[0][0].values.length;
			blockArea = cellsPerBlockX * cellsPerBlockY;
		}

		computeCells(binnedData, region);
		computeBlocks(cells);

		if (output == null || output.values.length != blocks[0].length * blocks.length * blockLength)
			output = new Histogram(blocks[0].length * blocks.length * blockLength);

		for (int j = 0, k = 0; j < blocks.length; j++) {
			for (int i = 0; i < blocks[0].length; i++, k++) {
				norm.normalise(blocks[j][i], blockArea);

				System.arraycopy(blocks[j][i].values, 0, output.values, k * blockLength, blockLength);
			}
		}

		return output;
	}

	private void computeBlocks(Histogram[][] cells) {
		for (int y = 0; y < numBlocksY; y++) {
			for (int x = 0; x < numBlocksX; x++) {
				final double[] blockData = blocks[y][x].values;

				for (int j = 0, k = 0; j < cellsPerBlockY; j++) {
					for (int i = 0; i < cellsPerBlockX; i++) {
						final double[] cellData = cells[y * blockStepY + j][x * blockStepX + i].values;

						System.arraycopy(cellData, 0, blockData, k, cellData.length);

						k += cellData.length;
					}
				}
			}
		}
	}

	private void computeCells(WindowedHistogramExtractor binnedData, Rectangle region) {
		final int cellWidth = (int) (region.width / numCellsX);
		final int cellHeight = (int) (region.height / numCellsY);

		for (int j = 0, y = (int) region.y; j < numCellsY; j++, y += cellHeight) {
			for (int i = 0, x = (int) region.x; i < numCellsX; i++, x += cellWidth) {
				binnedData.computeHistogram(x, y, cellWidth, cellHeight, cells[j][i]);
				cells[j][i].normaliseL2();
			}
		}
	}
}
