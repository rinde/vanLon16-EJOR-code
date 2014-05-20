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
import rinde.sim.pdptw.experiment.Experiment;
import rinde.sim.pdptw.experiment.Experiment.ExperimentResults;
import rinde.sim.pdptw.experiment.Experiment.SimulationResult;
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

  public static void main(String[] args) {

    final ObjectiveFunction objFunc = Gendreau06ObjectiveFunction.instance();

    final File[] files = new File("files/dataset/")
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

    final ExperimentResults results = Experiment.build(objFunc)
        .withThreads(1)
        .withRandomSeed(123)
        .addScenarios(scenarios)
        .addConfiguration(Central.solverConfiguration(
            CheapestInsertionHeuristic.supplier(objFunc),
            "-CheapestInsertion"))
        .perform();

    // urgency
    // dynamism
    // value

    final Multimap<MASConfiguration, SimulationResult> groupedResults = LinkedHashMultimap
        .create();
    for (final SimulationResult sr : results.results) {
      groupedResults.put(sr.masConfiguration, sr);
    }

    for (final MASConfiguration config : groupedResults.keySet()) {
      final Collection<SimulationResult> group = groupedResults.get(config);

      final File configResult = new File("files/dataset/" + config.toString()
          + ".csv");
      try {
        Files.createParentDirs(configResult);
      } catch (final IOException e1) {
        throw new IllegalStateException(e1);
      }
      configResult.delete();

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
          final long urgency = Long.parseLong(properties.get("urgency_mean"));

          final double cost = objFunc.computeCost(sr.stats);

          Files.append(Joiner.on(",").join(asList(dynamism, urgency, cost)),
              configResult, Charsets.UTF_8);

        } catch (final IOException e) {
          throw new IllegalStateException(e);
        }
      }

    }
  }
}
