package rinde.logistics.pdptw.mas;

import static com.google.common.collect.Lists.newArrayList;
import static rinde.sim.util.SupplierRngs.constant;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.stat.descriptive.StatisticalSummary;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.eclipse.swt.SWT;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import rinde.logistics.pdptw.mas.comm.AuctionCommModel;
import rinde.logistics.pdptw.mas.comm.SolverBidder;
import rinde.logistics.pdptw.mas.route.SolverRoutePlanner;
import rinde.logistics.pdptw.solver.CheapestInsertionHeuristic;
import rinde.sim.core.Simulator;
import rinde.sim.core.model.pdp.Depot;
import rinde.sim.core.model.pdp.Parcel;
import rinde.sim.core.model.pdp.TimeWindowPolicy.TimeWindowPolicies;
import rinde.sim.core.model.pdp.Vehicle;
import rinde.sim.pdptw.common.DynamicPDPTWProblem.StopConditions;
import rinde.sim.pdptw.common.ObjectiveFunction;
import rinde.sim.pdptw.common.TimeLinePanel;
import rinde.sim.pdptw.experiment.Experiment;
import rinde.sim.pdptw.gendreau06.Gendreau06ObjectiveFunction;
import rinde.sim.pdptw.measure.Analysis;
import rinde.sim.pdptw.scenario.Depots;
import rinde.sim.pdptw.scenario.IntensityFunctions;
import rinde.sim.pdptw.scenario.Locations;
import rinde.sim.pdptw.scenario.Metrics;
import rinde.sim.pdptw.scenario.Models;
import rinde.sim.pdptw.scenario.PDPScenario;
import rinde.sim.pdptw.scenario.PDPScenario.ProblemClass;
import rinde.sim.pdptw.scenario.PDPScenario.SimpleProblemClass;
import rinde.sim.pdptw.scenario.Parcels;
import rinde.sim.pdptw.scenario.ScenarioGenerator;
import rinde.sim.pdptw.scenario.ScenarioIO;
import rinde.sim.pdptw.scenario.TimeSeries;
import rinde.sim.pdptw.scenario.TimeSeries.TimeSeriesGenerator;
import rinde.sim.pdptw.scenario.TimeWindows;
import rinde.sim.pdptw.scenario.Vehicles;
import rinde.sim.scenario.ScenarioController.UICreator;
import rinde.sim.ui.View;
import rinde.sim.ui.renderers.PDPModelRenderer;
import rinde.sim.ui.renderers.PlaneRoadModelRenderer;
import rinde.sim.ui.renderers.RoadUserRenderer;
import rinde.sim.ui.renderers.UiSchema;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Multiset;
import com.google.common.collect.Table;
import com.google.common.io.Files;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Longs;

public class Generator {

  enum ExampleProblemClass implements ProblemClass {
    EXAMPLE;

    @Override
    public String getId() {
      return name();
    }
  }

  public static void main(String[] args) {
    main2(args);
    final PDPScenario scen;
    try {
      scen = ScenarioIO.read(new File(
          "files/dataset/h1.000-u50#4.scen"));
    } catch (final IOException e) {
      throw new IllegalStateException();
    }
    run(scen);

  }

  public static void run(PDPScenario s) {
    final ObjectiveFunction objFunc = Gendreau06ObjectiveFunction.instance();
    Experiment
        .build(Gendreau06ObjectiveFunction.instance())
        .addScenario(s)
        .addConfiguration(
            new TruckConfiguration(SolverRoutePlanner
                .supplier(CheapestInsertionHeuristic.supplier(objFunc)),
                SolverBidder.supplier(objFunc,
                    CheapestInsertionHeuristic.supplier(objFunc)),
                ImmutableList.of(AuctionCommModel.supplier())))

        .showGui(new UICreator() {

          @Override
          public void createUI(Simulator sim) {
            final UiSchema schema = new UiSchema(false);
            schema.add(Vehicle.class, SWT.COLOR_RED);
            schema.add(Depot.class, SWT.COLOR_CYAN);
            schema.add(Parcel.class, SWT.COLOR_BLUE);
            View.create(sim)
                .with(new PlaneRoadModelRenderer())
                .with(new RoadUserRenderer(schema, false))
                .with(new PDPModelRenderer())
                .with(new TimeLinePanel())
                .show();
          }
        })
        .perform();
  }

  public static void main2(String[] args) {
    // TODO write search procedure for finding scenarios with dynamism

    final List<Long> urgencyLevels = Longs.asList(0, 5, 10, 15, 20, 25, 30, 35,
        40, 45, 50);

    // final List<Double> desiredDynamismLevels = Doubles.asList(0, 10, 20, 30,
    // 40, 50, 60, 70, 80, 90);
    final List<Double> intensityHeights =
        Doubles.asList(-.99, -.8, -.5, -.25, 0, .25, .5, .75, 1, 2, 5);

    // for levels of urgency
    // for levels of dynamism

    final long scenarioHours = 12;
    final long scenarioLength = scenarioHours * 60 * 60 * 1000L;
    final long oneAndHalfDiagonalTT = 1527351L;
    final int totalNumberOfOrders = 360;

    final ImmutableTable.Builder<Long, Double, ScenarioGenerator> tableBuilder = ImmutableTable
        .builder();

    for (final long urg : urgencyLevels) {
      final long urgency = urg * 60 * 1000L;
      for (int i = 0; i < intensityHeights.size(); i++) {
        final TimeSeriesGenerator tsg = TimeSeries.nonHomogenousPoisson(
            scenarioLength - urgency - oneAndHalfDiagonalTT,
            IntensityFunctions.sineIntensity()
                .area(totalNumberOfOrders / scenarioHours)
                .period(60 * 60 * 1000L)
                .height(intensityHeights.get(i))
                .build());

        final ProblemClass pc = new SimpleProblemClass(String.format(
            "h%1.3f-u%d", intensityHeights.get(i), urg));

        tableBuilder.put(urgency, intensityHeights.get(i),
            createGenerator(pc, scenarioLength, urgency, tsg));
      }
      final TimeSeriesGenerator tsg = TimeSeries.homogenousPoisson(
          scenarioLength - urgency - oneAndHalfDiagonalTT, totalNumberOfOrders);
      final ProblemClass pc = new SimpleProblemClass(String.format(
          "h%1.3f-u%d", Double.POSITIVE_INFINITY, urgency));
      tableBuilder.put(urgency, Double.POSITIVE_INFINITY,
          createGenerator(pc, scenarioLength, urgency, tsg));
    }

    final ImmutableTable<Long, Double, ScenarioGenerator> scenarioGenerators = tableBuilder
        .build();

    final RandomGenerator rng = new MersenneTwister(123L);
    for (final Table.Cell<Long, Double, ScenarioGenerator> cell : scenarioGenerators
        .cellSet()) {
      final List<PDPScenario> scenarios = newArrayList();

      final SummaryStatistics ss = new SummaryStatistics();
      while (scenarios.size() < 5) {
        final String problemClassId = cell.getValue().getProblemClass().getId();
        final String id = "#" + scenarios.size();
        final String fileName = "files/dataset/" + problemClassId + id;
        final PDPScenario scen = cell.getValue().generate(rng, id);
        Metrics.checkTimeWindowStrictness(scen);

        final StatisticalSummary urgency = Metrics.measureUrgency(scen);

        final long expectedUrgency = cell.getRowKey();

        System.out.println("urgency: " + urgency.getMean() + " "
            + expectedUrgency);

        if (Math.abs(urgency.getMean() - expectedUrgency) < 0.01
            && urgency.getStandardDeviation() < 0.01) {
          System.out.println(" > ACCEPT");
          // System.out.println(urgency.getMean() + " +- "
          // + urgency.getStandardDeviation());

          final double dynamism = Metrics.measureDynamism(scen);

          try {
            Files.createParentDirs(new File(fileName));
            writePropertiesFile(scen, urgency, dynamism, problemClassId, id,
                fileName);

            // Analysis.writeLoads(Metrics.measureRelativeLoad(scen), new File(
            // fileName + ".load"));
            Analysis.writeLocationList(Metrics.getServicePoints(scen),
                new File(fileName + ".points"));
            Analysis.writeTimes(scen.getTimeWindow().end,
                Metrics.getArrivalTimes(scen), new File(fileName + ".times"));

            ScenarioIO.write(scen, new File(fileName + ".scen"));

          } catch (final IOException e) {
            throw new IllegalStateException(e);
          }
          // System.out.println(dynamism);
          // ss.addValue(dynamism * 100d);
          scenarios.add(scen);
          return;
        }
        else {
          // run(scen);
        }
      }

      System.out.println(ss.getMean() + " +- " + ss.getStandardDeviation());
    }

  }

  static void writePropertiesFile(PDPScenario scen, StatisticalSummary urgency,
      double dynamism, String problemClassId, String instanceId, String fileName) {
    final DateTimeFormatter formatter = ISODateTimeFormat
        .dateHourMinuteSecondMillis();
    final ImmutableMap.Builder<String, Object> properties = ImmutableMap
        .<String, Object> builder()
        .put("problem_class", problemClassId)
        .put("id", instanceId)
        .put("dynamism", dynamism)
        .put("urgency_mean", urgency.getMean())
        .put("urgency_sd", urgency.getStandardDeviation())
        .put("creation_date",
            formatter.print(System.currentTimeMillis()));

    final ImmutableMultiset<Enum<?>> eventTypes = Metrics
        .getEventTypeCounts(scen);
    for (final Multiset.Entry<Enum<?>> en : eventTypes.entrySet()) {
      properties.put(en.getElement().name(), en.getCount());
    }

    try {
      Files
          .write(
              Joiner.on("\n").withKeyValueSeparator(" = ")
                  .join(properties.build()),
              new File(fileName + ".properties"), Charsets.UTF_8);
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  static ScenarioGenerator createGenerator(ProblemClass pc,
      long scenarioLength,
      long urgency, TimeSeriesGenerator tsg) {
    return ScenarioGenerator
        .builder(pc)
        // global
        .timeUnit(SI.MILLI(SI.SECOND))
        .scenarioLength(scenarioLength)
        .tickSize(1000L)
        .speedUnit(NonSI.KILOMETERS_PER_HOUR)
        .distanceUnit(SI.KILOMETER)
        .stopCondition(
            Predicates.and(StopConditions.VEHICLES_DONE_AND_BACK_AT_DEPOT,
                StopConditions.TIME_OUT_EVENT))

        // parcels
        .parcels(
            Parcels
                .builder()
                .announceTimes(tsg)
                .pickupDurations(constant(5 * 60 * 1000L))
                .deliveryDurations(constant(5 * 60 * 1000L))
                .neededCapacities(constant(0))
                .locations(Locations.builder()
                    .min(0d)
                    .max(10d)
                    .uniform())
                .timeWindows(TimeWindows.builder()
                    .pickupUrgency(constant(urgency))
                    .pickupTimeWindowLength(constant(5 * 60 * 1000L))
                    .deliveryOpening(constant(0L))
                    .minDeliveryLength(constant(10 * 60 * 1000L))
                    .deliveryLengthFactor(constant(3d))
                    .build())
                .build())

        // vehicles
        .vehicles(
            Vehicles.builder()
                .capacities(constant(1))
                .centeredStartPositions()
                .creationTimes(constant(-1L))
                .numberOfVehicles(constant(10))
                .speeds(constant(50d))
                .timeWindowsAsScenario()
                .build())

        // depots
        .depots(Depots.singleCenteredDepot())

        // models
        .addModel(Models.roadModel(50d, true))
        .addModel(Models.pdpModel(TimeWindowPolicies.TARDY_ALLOWED))
        .build();
  }
}
