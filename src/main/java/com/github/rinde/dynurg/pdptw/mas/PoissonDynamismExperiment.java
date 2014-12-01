/*
 * Copyright (C) 2014 Rinde van Lon, iMinds DistriNet, KU Leuven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.rinde.dynurg.pdptw.mas;

import static com.google.common.collect.Lists.newArrayList;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import com.github.rinde.rinsim.scenario.generator.IntensityFunctions;
import com.github.rinde.rinsim.scenario.generator.Metrics;
import com.github.rinde.rinsim.scenario.generator.TimeSeries;
import com.github.rinde.rinsim.scenario.generator.TimeSeries.TimeSeriesGenerator;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.github.rinde.rinsim.util.StochasticSuppliers;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.Files;

public class PoissonDynamismExperiment {
  private static final double LENGTH_OF_DAY = 12 * 60 * 60 * 1000;
  private static final int NUM_EVENTS = 360;
  private static final int REPETITIONS = 1000;
  private static final long INTENSITY_PERIOD = 60 * 60 * 1000L;

  private static final String FOLDER = "files/results/time-series-dynamism-experiment/";

  public static void main(String[] args) {
    final RandomGenerator rng = new MersenneTwister(123L);
    final TimeSeriesGenerator nonHomogPoissonGenerator = TimeSeries
        .nonHomogenousPoisson(LENGTH_OF_DAY, IntensityFunctions
            .sineIntensity()
            .area(NUM_EVENTS / (LENGTH_OF_DAY / INTENSITY_PERIOD))
            .period(INTENSITY_PERIOD)
            .height(StochasticSuppliers.uniformDouble(-.99, 1.5d))
            .phaseShift(
                StochasticSuppliers.uniformDouble(0, INTENSITY_PERIOD))
            .buildStochasticSupplier());

    final TimeSeriesGenerator homogPoissonGenerator = TimeSeries
        .homogenousPoisson(LENGTH_OF_DAY, NUM_EVENTS);

    final TimeSeriesGenerator normalGenerator = TimeSeries.normal(
        LENGTH_OF_DAY, NUM_EVENTS, 2.4 * 60 * 1000);

    final StochasticSupplier<Double> maxDeviation = StochasticSuppliers
        .normal()
        .mean(1 * 60 * 1000)
        .std(1 * 60 * 1000)
        .lowerBound(0)
        .upperBound(15d * 60 * 1000)
        .buildDouble();
    final TimeSeriesGenerator uniformGenerator = TimeSeries.uniform(
        LENGTH_OF_DAY, NUM_EVENTS, maxDeviation);

    createDynamismHistogram(nonHomogPoissonGenerator, rng.nextLong(), new File(
        FOLDER + "non-homog-poisson-dynamism.csv"), REPETITIONS);

    createDynamismHistogram(homogPoissonGenerator, rng.nextLong(), new File(
        FOLDER + "homog-poisson-dynamism.csv"), REPETITIONS);

    createDynamismHistogram(normalGenerator, rng.nextLong(), new File(
        FOLDER + "normal-dynamism.csv"), REPETITIONS);

    createDynamismHistogram(uniformGenerator, rng.nextLong(), new File(
        FOLDER + "uniform-dynamism.csv"), REPETITIONS);

  }

  static void createDynamismHistogram(TimeSeriesGenerator generator, long seed,
      File file, int repetitions) {
    try {
      Files.createParentDirs(file);
    } catch (final IOException e1) {
      throw new IllegalStateException(e1);
    }
    final RandomGenerator rng = new MersenneTwister(seed);
    final List<Double> values = newArrayList();
    final SummaryStatistics ss = new SummaryStatistics();
    for (int i = 0; i < repetitions; i++) {
      final List<Double> times = generator.generate(rng.nextLong());
      ss.addValue(times.size());
      final double dynamism = Metrics.measureDynamism(times, LENGTH_OF_DAY);
      values.add(dynamism);
    }
    System.out.println(file.getName() + " has #events: mean: " + ss.getMean()
        + " +- " + ss.getStandardDeviation());

    final StringBuilder sb = new StringBuilder();
    sb.append(Joiner.on("\n").join(values));
    try {
      Files.write(sb.toString(), file, Charsets.UTF_8);
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
