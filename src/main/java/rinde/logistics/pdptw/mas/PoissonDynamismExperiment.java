package rinde.logistics.pdptw.mas;

import static com.google.common.collect.Lists.newArrayList;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;

import rinde.sim.pdptw.scenario.Metrics;
import rinde.sim.pdptw.scenario.TimeSeries;
import rinde.sim.pdptw.scenario.TimeSeries.TimeSeriesGenerator;

import com.google.common.base.Charsets;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import com.google.common.io.Files;

public class PoissonDynamismExperiment {
  private static final double LENGTH_OF_DAY = 12 * 60 * 60 * 1000;
  private static final int NUM_EVENTS = 360;
  private static final int REPETITIONS = 10000;

  public static void main(String[] args) {

    final RandomGenerator rng = new MersenneTwister(123L);
    final TimeSeriesGenerator poissonGenerator = TimeSeries.homogenousPoisson(
        LENGTH_OF_DAY, NUM_EVENTS);

    final TimeSeriesGenerator uniformGenerator = TimeSeries.uniform(
        LENGTH_OF_DAY, NUM_EVENTS, 30 * 60 * 1000);

    createDynamismHistogram(poissonGenerator, rng.nextLong(), new File(
        "files/results/poisson-dynamism.csv"), REPETITIONS);

    createDynamismHistogram(uniformGenerator, rng.nextLong(), new File(
        "files/results/uniform-dynamism.csv"), REPETITIONS);

  }

  static void createDynamismHistogram(TimeSeriesGenerator generator, long seed,
      File file, int repetitions) {
    final RandomGenerator rng = new MersenneTwister(seed);
    final List<Double> values = newArrayList();
    for (int i = 0; i < repetitions; i++) {
      final List<Double> times = generator.generate(rng.nextLong());
      final double dynamism = Metrics.measureDynamism(times, LENGTH_OF_DAY);
      values.add(dynamism);
    }
    final Multiset<Double> hist = Metrics.computeHistogram(values, 0.01);
    final StringBuilder sb = new StringBuilder();
    for (final Entry<Double> entry : hist.entrySet()) {
      sb.append(String.format("%1.2f", entry.getElement()))
          .append(",")
          .append(entry.getCount())
          .append(System.lineSeparator());
    }

    try {
      Files.write(sb.toString(), file, Charsets.UTF_8);
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

}
