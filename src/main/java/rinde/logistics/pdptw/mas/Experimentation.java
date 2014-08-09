package rinde.logistics.pdptw.mas;

import static java.util.Arrays.asList;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import rinde.sim.pdptw.experiment.Experiment.Builder;
import rinde.sim.pdptw.experiment.Experiment.SimulationResult;
import rinde.sim.pdptw.experiment.ExperimentCli;
import rinde.sim.pdptw.experiment.ExperimentResults;
import rinde.sim.pdptw.experiment.MASConfiguration;
import rinde.sim.pdptw.gendreau06.Gendreau06ObjectiveFunction;
import rinde.sim.scenario.AddVehicleEvent;
import rinde.sim.scenario.Scenario;
import rinde.sim.scenario.ScenarioIO;
import rinde.sim.util.cli.ArgHandler;
import rinde.sim.util.cli.ArgumentParser;
import rinde.sim.util.cli.Menu;
import rinde.sim.util.cli.Option;
import rinde.sim.util.io.FileProvider;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
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
    System.out.println(System.getProperty("jppf.config"));

    final long time = System.currentTimeMillis();
    final Experiment.Builder experimentBuilder = Experiment
        .build(SUM)
        .computeDistributed()
        .withRandomSeed(123)
        .repeat(10)
        .numBatches(10)
        .addScenarios(FileProvider.builder()
            .add(Paths.get(DATASET))
            .filter("glob:**[01].[0-9]0#[0-5].scen")
        )
        .addResultListener(new CommandLineProgress(System.out))
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
                "-dfsOpt2-CheapInsert-Dist"));

    final Menu m = ExperimentCli.createMenuBuilder(experimentBuilder)
        .add(Option.builder("nv", ArgumentParser.INTEGER)
            .longName("number-of-vehicles")
            .description("Changes the number of vehicles in all scenarios.")
            .build(),
            experimentBuilder,
            new ArgHandler<Experiment.Builder, Integer>() {
              @Override
              public void execute(Builder subject, Optional<Integer> argument) {
                subject.setScenarioReader(new NumVehiclesScenarioParser(
                    argument.get()));
              }
            })
        .build();

    final Optional<String> error = m.safeExecute(args);
    if (error.isPresent()) {
      System.err.println(error.get());
      return;
    }
    final ExperimentResults results = experimentBuilder.perform();

    final long duration = System.currentTimeMillis() - time;
    System.out.println("Done, computed " + results.results.size()
        + " simulations in " + duration / 1000d + "s");

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
            .append(
                "dynamism,urgency_mean,urgency_sd,cost,travel_time,tardiness,over_time,is_valid,scenario_id,random_seed,comp_time,num_vehicles\n",
                configResult,
                Charsets.UTF_8);
      } catch (final IOException e1) {
        throw new IllegalStateException(e1);
      }

      for (final SimulationResult sr : group) {
        final String pc = sr.scenario.getProblemClass().getId();
        final String id = sr.scenario.getProblemInstanceId();
        final int numVehicles = FluentIterable.from(sr.scenario.asList())
            .filter(AddVehicleEvent.class).size();
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
              .appendTo(new StringBuilder(),
                  asList(dynamism, urgencyMean, urgencySd, cost, travelTime,
                      tardiness, overTime, isValidResult, pc + id, sr.seed,
                      computationTime, numVehicles))
              .append(System.lineSeparator())
              .toString();
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

  static class NumVehiclesScenarioParser implements Function<Path, Scenario> {
    final int numVehicles;

    NumVehiclesScenarioParser(int num) {
      numVehicles = num;
    }

    @Override
    public Scenario apply(Path input) {
      Scenario scenario;
      try {
        scenario = ScenarioIO.read(input);
        return Scenario
            .builder(scenario.getProblemClass())
            .copyProperties(scenario)
            .ensureFrequency(
                Predicates.instanceOf(AddVehicleEvent.class),
                numVehicles)
            .build();
      } catch (final IOException e) {
        throw new IllegalStateException(e);
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
