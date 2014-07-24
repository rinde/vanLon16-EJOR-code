package rinde.logistics.pdptw.mas;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import rinde.logistics.pdptw.solver.CheapestInsertionHeuristic;
import rinde.logistics.pdptw.solver.Opt2;
import rinde.sim.pdptw.central.Central;
import rinde.sim.pdptw.common.ObjectiveFunction;
import rinde.sim.pdptw.common.StatisticsDTO;
import rinde.sim.pdptw.experiment.CommandLineProgress;
import rinde.sim.pdptw.experiment.Experiment;
import rinde.sim.pdptw.experiment.Experiment.SimulationResult;
import rinde.sim.pdptw.experiment.ExperimentResults;
import rinde.sim.pdptw.experiment.MASConfiguration;
import rinde.sim.pdptw.gendreau06.Gendreau06ObjectiveFunction;
import rinde.sim.pdptw.scenario.PDPScenario;
import rinde.sim.pdptw.scenario.ScenarioIO;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;

public class Experimentation {

  static final Gendreau06ObjectiveFunction SUM = Gendreau06ObjectiveFunction
      .instance();
  static final ObjectiveFunction DISTANCE = new DistanceObjectiveFunction();
  static final ObjectiveFunction TARDINESS = new TardinessObjectiveFunction();

  static final String DATASET = "files/dataset/";
  static final String RESULTS = "files/results/";

  public static void main(String[] args) {
    System.out.println(Arrays.toString(args));
    System.out.println(System.getProperty("jppf.config"));

    System.out.print("Searching..");
    final File[] files = new File(DATASET)
        .listFiles(new FileFilter() {
          @Override
          public boolean accept(File pathname) {
            return pathname.getName().equals("0-0.00#0.scen");
          }
        });
    System.out.println(" found " + files.length + " scenarios.");
    System.out.print("Loading..");
    final List<PDPScenario> scenarios = newArrayList();
    for (final File file : files) {
      try {
        scenarios.add(ScenarioIO.read(file));
      } catch (final IOException e) {
        throw new IllegalStateException(e);
      }
    }
    System.out.println(" loaded " + scenarios.size() + " scenarios.");

    final long time = System.currentTimeMillis();
    final Optional<ExperimentResults> results = Experiment
        .build(SUM)
        .computeDistributed()
        .withRandomSeed(123)
        .repeat(10)
        .numBatches(10)
        .addScenarios(scenarios)
        .addResultListener(new CommandLineProgress())
        .addConfiguration(Central.solverConfiguration(
            CheapestInsertionHeuristic.supplier(SUM),
            "-CheapInsert"))
        .addConfiguration(Central.solverConfiguration(
            CheapestInsertionHeuristic.supplier(TARDINESS),
            "-CheapInsert-Tard"))
        .addConfiguration(Central.solverConfiguration(
            CheapestInsertionHeuristic.supplier(DISTANCE),
            "-CheapInsert-Dist"))
        .addConfiguration(
            Central.solverConfiguration(
                Opt2.breadthFirstSupplier(
                    CheapestInsertionHeuristic.supplier(SUM), SUM),
                "-bfsOpt2-CheapInsert"))
        .addConfiguration(
            Central.solverConfiguration(
                Opt2.breadthFirstSupplier(
                    CheapestInsertionHeuristic.supplier(TARDINESS),
                    TARDINESS),
                "-bfsOpt2-CheapInsert-Tard"))
        .addConfiguration(
            Central.solverConfiguration(
                Opt2.breadthFirstSupplier(
                    CheapestInsertionHeuristic.supplier(DISTANCE),
                    DISTANCE),
                "-bfsOpt2-CheapInsert-Dist"))
        .addConfiguration(
            Central.solverConfiguration(
                Opt2.depthFirstSupplier(
                    CheapestInsertionHeuristic.supplier(SUM), SUM),
                "-dfsOpt2-CheapInsert"))
        .addConfiguration(
            Central.solverConfiguration(
                Opt2.depthFirstSupplier(
                    CheapestInsertionHeuristic.supplier(TARDINESS),
                    TARDINESS),
                "-dfsOpt2-CheapInsert-Tard"))
        .addConfiguration(
            Central.solverConfiguration(
                Opt2.depthFirstSupplier(
                    CheapestInsertionHeuristic.supplier(DISTANCE),
                    DISTANCE),
                "-dfsOpt2-CheapInsert-Dist"))

        .perform(args);

    if (results.isPresent()) {
      final long duration = System.currentTimeMillis() - time;
      System.out.println("Done, computed " + results.get().results.size()
          + " simulations in " + duration / 1000d + "s");

      final Multimap<MASConfiguration, SimulationResult> groupedResults = LinkedHashMultimap
          .create();
      for (final SimulationResult sr : results.get().results) {
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
              .append(
                  "dynamism,urgency_mean,urgency_sd,cost,travel_time,tardiness,over_time,is_valid,scenario_id,random_seed,comp_time\n",
                  configResult,
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
            final double urgencyMean = Double.parseDouble(properties
                .get("urgency_mean"));
            final double urgencySd = Double.parseDouble(properties
                .get("urgency_sd"));

            final double cost = SUM.computeCost(sr.stats);
            final double travelTime = SUM.travelTime(sr.stats);
            final double tardiness = SUM.tardiness(sr.stats);
            final double overTime = SUM.overTime(sr.stats);
            final boolean isValidResult = SUM.isValidResult(sr.stats);
            final long computationTime = sr.stats.computationTime;

            final String line = Joiner.on(",")
                .join(
                    asList(dynamism, urgencyMean, urgencySd, cost, travelTime,
                        tardiness, overTime, isValidResult, pc + id, sr.seed,
                        computationTime), "\n");
            if (!isValidResult) {
              System.err.println("WARNING: FOUND AN INVALID RESULT: ");
              System.err.println(line);
            }
            Files.append(line, configResult, Charsets.UTF_8);
          } catch (final IOException e) {
            throw new IllegalStateException(e);
          }
        }
      }
    }
  }

  static class DistanceObjectiveFunction implements ObjectiveFunction,
      Serializable {
    private static final long serialVersionUID = 3604634797953982385L;

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

  static class TardinessObjectiveFunction implements ObjectiveFunction,
      Serializable {
    private static final long serialVersionUID = -3989091829481513511L;

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
