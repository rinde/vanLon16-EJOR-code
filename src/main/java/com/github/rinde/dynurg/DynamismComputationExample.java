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
package com.github.rinde.dynurg;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;

import java.util.Collections;
import java.util.List;

/**
 * Detailed example of computations for computing dynamism.
 * @author Rinde van Lon <rinde.vanlon@cs.kuleuven.be>
 */
public class DynamismComputationExample {

  /**
   * Just run it.
   * @param args Ignored.
   */
  public static void main(String[] args) {
    final Iterable<Double> a = asList(.9, 1d, 3d, 3.1, 5.1, 5.2, 7.2, 7.3, 9.3,
        9.4);
    final Iterable<Double> b = asList(.5, .6, .7, .8, .9, 1d, 3d, 5d, 7d, 9d);

    System.out.println("A");
    System.out.println("dynamism: " + measureDynamism(a, 10));
    System.out.println("B");
    System.out.println("dynamism: " + measureDynamism(b, 10));
  }

  private static double measureDynamism(Iterable<Double> arrivalTimes,
      double lengthOfDay) {
    final List<Double> times = newArrayList(arrivalTimes);
    checkArgument(times.size() >= 2,
        "At least two arrival times are required, found %s time(s).",
        times.size());
    for (final double time : times) {
      checkArgument(time >= 0 && time < lengthOfDay,
          "all specified times should be >= 0 and < %s. Found %s.",
          lengthOfDay, time);
    }
    Collections.sort(times);

    final int numEvents = times.size();

    // this is the expected interarrival time
    final double expectedInterArrivalTime = lengthOfDay
        / numEvents;

    final List<Double> deltas = newArrayList();
    final List<Double> deviations = newArrayList();
    final List<Double> maxDeviations = newArrayList();

    // deviation to expectedInterArrivalTime
    double sumDeviation = 0;
    double maxDeviation = (numEvents - 1) * expectedInterArrivalTime;
    double prevDeviation = 0;
    for (int i = 0; i < numEvents - 1; i++) {
      // compute interarrival time
      final double delta = times.get(i + 1) - times.get(i);
      deltas.add(delta);
      if (delta < expectedInterArrivalTime) {
        final double diff = expectedInterArrivalTime - delta;
        final double scaledPrev = diff / expectedInterArrivalTime
            * prevDeviation;
        final double cur = diff + scaledPrev;
        deviations.add(cur);
        maxDeviations.add(expectedInterArrivalTime + scaledPrev);
        sumDeviation += cur;
        maxDeviation += scaledPrev;
        prevDeviation = cur;
      } else {
        deviations.add(0d);
        maxDeviations.add(expectedInterArrivalTime);
        prevDeviation = 0;
      }
    }
    System.out.println("E = " + times);
    System.out.println("T = " + lengthOfDay);
    System.out.println("theta = " + expectedInterArrivalTime);
    System.out.println("Delta = " + deltas);
    System.out
        .println("deviations: " + deviations + " sum: " + sum(deviations));
    System.out.println("max deviations: " + maxDeviations + " sum: "
        + sum(maxDeviations));
    return 1d - sumDeviation / maxDeviation;
  }

  private static double sum(List<Double> list) {
    double sum = 0d;
    for (final Double d : list) {
      sum += d;
    }
    return sum;
  }
}
