package rinde.logistics.pdptw.mas;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newLinkedHashMap;
import static rinde.sim.util.StochasticSuppliers.constant;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.stat.descriptive.StatisticalSummary;
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
import rinde.sim.util.StochasticSupplier;
import rinde.sim.util.StochasticSuppliers;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.io.Files;
import com.google.common.primitives.Longs;

public class Generator {
  // all times are in ms unless otherwise indicated

  private static final long TICK_SIZE = 1000L;
  private static final double VEHICLE_SPEED_KMH = 50d;
  private static final int NUM_VEHICLES = 10;
  private static final double AREA_WIDTH = 10;

  private static final long SCENARIO_HOURS = 12L;
  private static final long SCENARIO_LENGTH = SCENARIO_HOURS * 60 * 60 * 1000L;
  private static final int NUM_ORDERS = 360;

  private static final long HALF_DIAG_TT = 509117L;
  private static final long ONE_AND_HALF_DIAG_TT = 1527351L;
  private static final long TWO_DIAG_TT = 2036468L;

  private static final long PICKUP_DURATION = 5 * 60 * 1000L;
  private static final long DELIVERY_DURATION = 5 * 60 * 1000L;

  private static final long INTENSITY_PERIOD = 60 * 60 * 1000L;

  private static final int TARGET_NUM_INSTANCES = 10;

  public static void main(String[] args) {
    main2(args);
    final PDPScenario scen;
    try {
      scen = ScenarioIO.read(new File(
          "files/dataset/0-0.60#0.scen"));
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

  static class GeneratorSettings {
    final TimeSeriesType timeSeriesType;
    final long urgency;
    final long dayLength;
    final long officeHours;
    final ImmutableMap<String, String> properties;

    GeneratorSettings(TimeSeriesType type, long urg, long dayLen, long officeH,
        Map<String, String> props) {
      timeSeriesType = type;
      urgency = urg;
      dayLength = dayLen;
      officeHours = officeH;
      properties = ImmutableMap.copyOf(props);
    }
  }

  enum TimeSeriesType {
    SINE, HOMOGENOUS, UNIFORM;
  }

  public static void main2(String[] args) {
    final List<Long> urgencyLevels = Longs.asList(0, 5, 10, 15, 20, 25, 30, 35,
        40, 45);

    final ImmutableMap.Builder<GeneratorSettings, ScenarioGenerator> generatorsMap = ImmutableMap
        .builder();

    for (final long urg : urgencyLevels) {
      final long urgency = urg * 60 * 1000L;
      // The office hours is the period in which new orders are accepted, it
      // is defined as [0,officeHoursLength).
      final long officeHoursLength;
      if (urgency < HALF_DIAG_TT) {
        officeHoursLength = SCENARIO_LENGTH - TWO_DIAG_TT - PICKUP_DURATION
            - DELIVERY_DURATION;
      } else {
        officeHoursLength = SCENARIO_LENGTH - urgency - ONE_AND_HALF_DIAG_TT
            - PICKUP_DURATION - DELIVERY_DURATION;
      }

      final double numPeriods = officeHoursLength / (double) INTENSITY_PERIOD;

      final Map<String, String> props = newLinkedHashMap();
      props.put("expected_num_orders", Integer.toString(NUM_ORDERS));
      props.put("time_series", "sine Poisson ");
      props.put("time_series.period", Long.toString(INTENSITY_PERIOD));
      props.put("time_series.num_periods", Double.toString(numPeriods));
      props.put("pickup_duration", Long.toString(PICKUP_DURATION));
      props.put("delivery_duration", Long.toString(DELIVERY_DURATION));
      props.put("width_height",
          String.format("%1.1fx%1.1f", AREA_WIDTH, AREA_WIDTH));
      final GeneratorSettings sineSettings = new GeneratorSettings(
          TimeSeriesType.SINE, urg, SCENARIO_LENGTH, officeHoursLength, props);

      final TimeSeriesGenerator sineTsg = TimeSeries.nonHomogenousPoisson(
          officeHoursLength,
          IntensityFunctions
              .sineIntensity()
              .area(NUM_ORDERS / numPeriods)
              .period(INTENSITY_PERIOD)
              .height(StochasticSuppliers.uniformDouble(-.99, 3d))
              .phaseShift(
                  StochasticSuppliers.uniformDouble(0, INTENSITY_PERIOD))
              .buildStochasticSupplier());

      props.put("time_series", "homogenous Poisson");
      props.put("time_series.intensity",
          Double.toString((double) NUM_ORDERS / (double) officeHoursLength));
      props.remove("time_series.period");
      props.remove("time_series.num_periods");
      final TimeSeriesGenerator homogTsg = TimeSeries.homogenousPoisson(
          officeHoursLength, NUM_ORDERS);
      final GeneratorSettings homogSettings = new GeneratorSettings(
          TimeSeriesType.HOMOGENOUS, urg, SCENARIO_LENGTH, officeHoursLength,
          props);

      final StochasticSupplier<Double> maxDeviation = StochasticSuppliers
          .uniformDouble(0, 15 * 60 * 1000);
      props.put("time_series", "uniform");
      // props.put("time_series.max_deviation", Long.toString(maxDeviation));
      props.remove("time_series.intensity");
      final TimeSeriesGenerator uniformTsg = TimeSeries.uniform(
          officeHoursLength, NUM_ORDERS, maxDeviation);
      final GeneratorSettings uniformSettings = new GeneratorSettings(
          TimeSeriesType.UNIFORM, urg, SCENARIO_LENGTH, officeHoursLength,
          props);

      generatorsMap.put(sineSettings,
          createGenerator(SCENARIO_LENGTH, urgency, sineTsg));
      generatorsMap.put(homogSettings,
          createGenerator(SCENARIO_LENGTH, urgency, homogTsg));
      generatorsMap.put(uniformSettings,
          createGenerator(SCENARIO_LENGTH, urgency, uniformTsg));
    }

    final ImmutableMap<GeneratorSettings, ScenarioGenerator> scenarioGenerators = generatorsMap
        .build();

    final RandomGenerator rng = new MersenneTwister(123L);
    for (final Entry<GeneratorSettings, ScenarioGenerator> entry : scenarioGenerators
        .entrySet()) {

      final GeneratorSettings generatorSettings = entry.getKey();
      System.out.println("URGENCY: " + generatorSettings.urgency);

      if (generatorSettings.timeSeriesType == TimeSeriesType.SINE) {
        createScenarios(rng, generatorSettings, entry.getValue(), .0, .51, 6);
      } else if (generatorSettings.timeSeriesType == TimeSeriesType.HOMOGENOUS) {
        createScenarios(rng, generatorSettings, entry.getValue(), .59, .61, 1);
      } else if (generatorSettings.timeSeriesType == TimeSeriesType.UNIFORM) {
        createScenarios(rng, generatorSettings, entry.getValue(), .69, 1, 4);
      }

    }
  }

  static void createScenarios(RandomGenerator rng,
      GeneratorSettings generatorSettings, ScenarioGenerator generator,
      double dynLb, double dynUb, int levels) {
    final List<PDPScenario> scenarios = newArrayList();

    final Multimap<Double, PDPScenario> dynamismScenariosMap = LinkedHashMultimap
        .create();
    System.out.println(generatorSettings.timeSeriesType);
    while (scenarios.size() < levels * TARGET_NUM_INSTANCES) {
      final PDPScenario scen = generator.generate(rng, "temp");
      Metrics.checkTimeWindowStrictness(scen);
      final StatisticalSummary urgency = Metrics.measureUrgency(scen);

      final long expectedUrgency = generatorSettings.urgency * 60000L;
      if (Math.abs(urgency.getMean() - expectedUrgency) < 0.01
          && urgency.getStandardDeviation() < 0.01) {

        // System.out.println(urgency.getMean() + " +- "
        // + urgency.getStandardDeviation());

        final double dynamism = Metrics.measureDynamism(scen,
            generatorSettings.officeHours);
        System.out.print(String.format("%1.3f ", dynamism));
        if ((dynamism % 0.1 < 0.01 || dynamism % 0.1 > 0.09)
            && dynamism <= dynUb && dynamism >= dynLb) {

          final double targetDyn = Math.round(dynamism * 10d) / 10d;

          final int numInstances = dynamismScenariosMap.get(targetDyn).size();

          if (numInstances < TARGET_NUM_INSTANCES) {

            final String instanceId = "#"
                + Integer.toString(numInstances);
            dynamismScenariosMap.put(targetDyn, scen);

            final String problemClassId = String.format("%d-%1.2f",
                (long) (urgency.getMean() / 60000),
                targetDyn);
            System.out.println();
            System.out.println(" > ACCEPT " + problemClassId);
            final String fileName = "files/dataset/" + problemClassId
                + instanceId;
            try {
              Files.createParentDirs(new File(fileName));
              writePropertiesFile(scen, urgency, dynamism, problemClassId,
                  instanceId, generatorSettings, fileName);
              Analysis.writeLocationList(Metrics.getServicePoints(scen),
                  new File(fileName + ".points"));
              Analysis.writeTimes(scen.getTimeWindow().end,
                  Metrics.getArrivalTimes(scen),
                  new File(fileName + ".times"));

              final ProblemClass pc = new SimpleProblemClass(problemClassId);
              final PDPScenario finalScenario = PDPScenario.builder(pc)
                  .copyProperties(scen)
                  .problemClass(pc)
                  .instanceId(instanceId)
                  .build();

              ScenarioIO.write(finalScenario, new File(fileName + ".scen"));
            } catch (final IOException e) {
              throw new IllegalStateException(e);
            }
            // System.out.println(dynamism);
            // ss.addValue(dynamism * 100d);
            scenarios.add(scen);
          }
          // return;
        }

      }
      else {
        // run(scen);
      }
    }
  }

  static void writePropertiesFile(PDPScenario scen, StatisticalSummary urgency,
      double dynamism, String problemClassId, String instanceId,
      GeneratorSettings settings, String fileName) {
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
            formatter.print(System.currentTimeMillis()))
        .put("creator", System.getProperty("user.name"))
        .put("day_length", settings.dayLength)
        .put("office_opening_hours", settings.officeHours);

    properties.putAll(settings.properties);

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

  static ScenarioGenerator createGenerator(long scenarioLength,
      long urgency, TimeSeriesGenerator tsg) {
    return ScenarioGenerator
        .builder()
        // global
        .timeUnit(SI.MILLI(SI.SECOND))
        .scenarioLength(scenarioLength)
        .tickSize(TICK_SIZE)
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
                .pickupDurations(constant(PICKUP_DURATION))
                .deliveryDurations(constant(DELIVERY_DURATION))
                .neededCapacities(constant(0))
                .locations(Locations.builder()
                    .min(0d)
                    .max(AREA_WIDTH)
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
                .numberOfVehicles(constant(NUM_VEHICLES))
                .speeds(constant(VEHICLE_SPEED_KMH))
                .timeWindowsAsScenario()
                .build())

        // depots
        .depots(Depots.singleCenteredDepot())

        // models
        .addModel(Models.roadModel(VEHICLE_SPEED_KMH, true))
        .addModel(Models.pdpModel(TimeWindowPolicies.TARDY_ALLOWED))
        .build();
  }
}
