package rinde.logistics.pdptw.mas;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import rinde.logistics.pdptw.solver.CheapestInsertionHeuristic;
import rinde.sim.pdptw.central.Central;
import rinde.sim.pdptw.common.ObjectiveFunction;
import rinde.sim.pdptw.common.StatisticsDTO;
import rinde.sim.pdptw.experiment.Experiment;
import rinde.sim.pdptw.experiment.Experiment.SimulationResult;
import rinde.sim.pdptw.experiment.ExperimentResults;
import rinde.sim.pdptw.experiment.MASConfiguration;
import rinde.sim.pdptw.gendreau06.Gendreau06ObjectiveFunction;
import rinde.sim.pdptw.scenario.PDPScenario;
import rinde.sim.pdptw.scenario.ScenarioIO;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;

public class Experimentation {

  static final ObjectiveFunction SUM = Gendreau06ObjectiveFunction.instance();
  static final ObjectiveFunction DISTANCE = new DistanceObjectiveFunction();
  static final ObjectiveFunction TARDINESS = new TardinessObjectiveFunction();

  static final String DATASET = "files/dataset/";
  static final String RESULTS = "files/results/";

  public static void main(String[] args) {

    final File[] files = new File(DATASET)
        .listFiles(new FileFilter() {
          @Override
          public boolean accept(File pathname) {
            return pathname.getName().endsWith(".scen");
          }
        });
    final List<PDPScenario> scenarios = newArrayList();
    for (final File file : files) {
      try {
        scenarios.add(ScenarioIO.read(file));
      } catch (final IOException e) {
        throw new IllegalStateException(e);
      }
    }

    final ExperimentResults results = Experiment
        .build(SUM)
        .withThreads(24)
        .withRandomSeed(123)
        .repeat(1)
        .addScenarios(scenarios)
        .addConfiguration(Central.solverConfiguration(
            CheapestInsertionHeuristic.supplier(SUM),
            "-CheapInsert"))
        // .addConfiguration(Central.solverConfiguration(
        // CheapestInsertionHeuristic.supplier(TARDINESS),
        // "-CheapInsert-Tard"))
        // .addConfiguration(Central.solverConfiguration(
        // CheapestInsertionHeuristic.supplier(DISTANCE),
        // "-CheapInsert-Dist"))
        // .addConfiguration(Central.solverConfiguration(
        // Opt2.supplier(CheapestInsertionHeuristic.supplier(SUM), SUM),
        // "-Opt2-CheapInsert"))
        // .addConfiguration(
        // Central.solverConfiguration(
        // Opt2.supplier(CheapestInsertionHeuristic.supplier(TARDINESS),
        // TARDINESS),
        // "-Opt2-CheapInsert-Tard"))
        // .addConfiguration(
        // Central.solverConfiguration(
        // Opt2.supplier(CheapestInsertionHeuristic.supplier(DISTANCE),
        // DISTANCE),
        // "-Opt2-CheapInsert-Dist"))

        .perform();

    final Multimap<MASConfiguration, SimulationResult> groupedResults = LinkedHashMultimap
        .create();
    for (final SimulationResult sr : results.results) {
      groupedResults.put(sr.masConfiguration, sr);
    }

    for (final MASConfiguration config : groupedResults.keySet()) {
      final Collection<SimulationResult> group = groupedResults.get(config);

      final File configResult = new File(RESULTS + config.toString()
          + ".csv");
      try {
        Files.createParentDirs(configResult);
      } catch (final IOException e1) {
        throw new IllegalStateException(e1);
      }
      // deletes the file in case it already exists
      configResult.delete();
      try {
        Files
            .append("dynamism,urgency_mean,cost\n", configResult,
                Charsets.UTF_8);
      } catch (final IOException e1) {
        throw new IllegalStateException(e1);
      }

      for (final SimulationResult sr : group) {
        final String pc = sr.scenario.getProblemClass().getId();
        final String id = sr.scenario.getProblemInstanceId();
        try {
          final List<String> propsStrings = Files.readLines(new File(
              "files/dataset/" + pc + id + ".properties"),
              Charsets.UTF_8);
          final Map<String, String> properties = Splitter.on("\n")
              .withKeyValueSeparator(" = ")
              .split(Joiner.on("\n").join(propsStrings));

          final double dynamism = Double
              .parseDouble(properties.get("dynamism"));
          final double urgency = Double.parseDouble(properties
              .get("urgency_mean"));

          final double cost = SUM.computeCost(sr.stats);

          Files.append(Joiner.on(",").join(asList(dynamism, urgency, cost))
              + "\n",
              configResult, Charsets.UTF_8);

        } catch (final IOException e) {
          throw new IllegalStateException(e);
        }
      }

    }
  }

  static class DistanceObjectiveFunction implements ObjectiveFunction {
    @Override
    public boolean isValidResult(StatisticsDTO stats) {
      return Gendreau06ObjectiveFunction.instance().isValidResult(stats);
    }

    @Override
    public double computeCost(StatisticsDTO stats) {
      return stats.totalDistance;
    }

    @Override
    public String printHumanReadableFormat(StatisticsDTO stats) {
      return String.format("Distance %1.3f.", stats.totalDistance);
    }
  }

  static class TardinessObjectiveFunction implements ObjectiveFunction {
    @Override
    public boolean isValidResult(StatisticsDTO stats) {
      return Gendreau06ObjectiveFunction.instance().isValidResult(stats);
    }

    @Override
    public double computeCost(StatisticsDTO stats) {
      return stats.pickupTardiness + stats.deliveryTardiness;
    }

    @Override
    public String printHumanReadableFormat(StatisticsDTO stats) {
      return String.format("Tardiness %1.3f.", stats.pickupTardiness
          + stats.deliveryTardiness);
    }
  }
}
