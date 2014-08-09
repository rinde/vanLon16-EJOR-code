package com.github.rinde.dynurg.pdptw.mas;

import static com.github.rinde.rinsim.util.StochasticSuppliers.constant;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newLinkedHashMap;

import java.io.File;
import java.io.IOException;
import java.math.RoundingMode;
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

import com.github.rinde.logistics.pdptw.mas.TruckConfiguration;
import com.github.rinde.logistics.pdptw.mas.comm.AuctionCommModel;
import com.github.rinde.logistics.pdptw.mas.comm.SolverBidder;
import com.github.rinde.logistics.pdptw.mas.route.SolverRoutePlanner;
import com.github.rinde.logistics.pdptw.solver.CheapestInsertionHeuristic;
import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.graph.Point;
import com.github.rinde.rinsim.core.model.pdp.Depot;
import com.github.rinde.rinsim.core.model.pdp.PDPScenarioEvent;
import com.github.rinde.rinsim.core.model.pdp.Parcel;
import com.github.rinde.rinsim.core.model.pdp.Vehicle;
import com.github.rinde.rinsim.core.model.pdp.TimeWindowPolicy.TimeWindowPolicies;
import com.github.rinde.rinsim.core.pdptw.ParcelDTO.Builder;
import com.github.rinde.rinsim.pdptw.common.ObjectiveFunction;
import com.github.rinde.rinsim.pdptw.common.TimeLinePanel;
import com.github.rinde.rinsim.pdptw.common.DynamicPDPTWProblem.StopConditions;
import com.github.rinde.rinsim.pdptw.experiment.Experiment;
import com.github.rinde.rinsim.pdptw.gendreau06.Gendreau06ObjectiveFunction;
import com.github.rinde.rinsim.pdptw.measure.Analysis;
import com.github.rinde.rinsim.pdptw.scenario.Depots;
import com.github.rinde.rinsim.pdptw.scenario.IntensityFunctions;
import com.github.rinde.rinsim.pdptw.scenario.Locations;
import com.github.rinde.rinsim.pdptw.scenario.Metrics;
import com.github.rinde.rinsim.pdptw.scenario.Models;
import com.github.rinde.rinsim.pdptw.scenario.Parcels;
import com.github.rinde.rinsim.pdptw.scenario.ScenarioGenerator;
import com.github.rinde.rinsim.pdptw.scenario.TimeSeries;
import com.github.rinde.rinsim.pdptw.scenario.Vehicles;
import com.github.rinde.rinsim.pdptw.scenario.ScenarioGenerator.TravelTimes;
import com.github.rinde.rinsim.pdptw.scenario.TimeSeries.TimeSeriesGenerator;
import com.github.rinde.rinsim.pdptw.scenario.TimeWindows.TimeWindowGenerator;
import com.github.rinde.rinsim.scenario.Scenario;
import com.github.rinde.rinsim.scenario.ScenarioIO;
import com.github.rinde.rinsim.scenario.Scenario.ProblemClass;
import com.github.rinde.rinsim.scenario.Scenario.SimpleProblemClass;
import com.github.rinde.rinsim.scenario.ScenarioController.UICreator;
import com.github.rinde.rinsim.ui.View;
import com.github.rinde.rinsim.ui.renderers.PDPModelRenderer;
import com.github.rinde.rinsim.ui.renderers.PlaneRoadModelRenderer;
import com.github.rinde.rinsim.ui.renderers.RoadUserRenderer;
import com.github.rinde.rinsim.ui.renderers.UiSchema;
import com.github.rinde.rinsim.util.StochasticSupplier;
import com.github.rinde.rinsim.util.StochasticSuppliers;
import com.github.rinde.rinsim.util.TimeWindow;
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
import com.google.common.math.DoubleMath;
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

  private static final int TARGET_NUM_INSTANCES = 50;

  // These parameters influence the dynamism selection settings
  private static final double DYN_STEP_SIZE = 0.05;
  private static final double DYN_BANDWIDTH = 0.01;

  public static void main(String[] args) {
    main2(args);
    final Scenario scen;
    final String fileName = "files/dataset/30-0.00#2.scen";
    try {
      scen = ScenarioIO.read(new File(fileName).toPath());
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
    run(scen, fileName);
  }

  public static void run(Scenario s, final String fileName) {
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
                .setTitleAppendix(fileName)
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
    SINE, HOMOGENOUS, NORMAL, UNIFORM;
  }

  public static void main2(String[] args) {
    final List<Long> urgencyLevels = Longs.asList(0, 5, 10, 15, 20, 25, 30, 35,
        40, 45);

    final ImmutableMap.Builder<GeneratorSettings, ScenarioGenerator> generatorsMap = ImmutableMap
        .builder();

    for (final long urg : urgencyLevels) {
      System.out.print("create " + urg);
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

      System.out.print(" non-homogenous Poisson");
      // NON-HOMOGENOUS
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

      System.out.print(" homogenous Poisson");
      // HOMOGENOUS
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

      System.out.print(" normal");
      // NORMAL
      props.put("time_series", "normal");
      props.remove("time_series.intensity");
      final TimeSeriesGenerator normalTsg = TimeSeries.normal(
          officeHoursLength, NUM_ORDERS, 2.4 * 60 * 1000);
      final GeneratorSettings normalSettings = new GeneratorSettings(
          TimeSeriesType.NORMAL, urg, SCENARIO_LENGTH, officeHoursLength,
          props);

      System.out.print(" uniform");
      // UNIFORM
      props.put("time_series", "uniform");
      final StochasticSupplier<Double> maxDeviation = StochasticSuppliers
          .normal()
          .mean(1 * 60 * 1000)
          .std(1 * 60 * 1000)
          .lowerBound(0)
          .upperBound(15d * 60 * 1000)
          .buildDouble();
      final TimeSeriesGenerator uniformTsg = TimeSeries.uniform(
          officeHoursLength, NUM_ORDERS, maxDeviation);
      final GeneratorSettings uniformSettings = new GeneratorSettings(
          TimeSeriesType.UNIFORM, urg, SCENARIO_LENGTH, officeHoursLength,
          props);
      System.out.println(".");

      generatorsMap.put(sineSettings,
          createGenerator(SCENARIO_LENGTH, urgency, sineTsg));
      generatorsMap.put(homogSettings,
          createGenerator(SCENARIO_LENGTH, urgency, homogTsg));
      generatorsMap.put(normalSettings,
          createGenerator(SCENARIO_LENGTH, urgency, normalTsg));
      generatorsMap.put(uniformSettings,
          createGenerator(SCENARIO_LENGTH, urgency, uniformTsg));
    }

    final ImmutableMap<GeneratorSettings, ScenarioGenerator> scenarioGenerators = generatorsMap
        .build();

    System.out.println("num generators: " + scenarioGenerators.size());
    final RandomGenerator rng = new MersenneTwister(123L);
    for (final Entry<GeneratorSettings, ScenarioGenerator> entry : scenarioGenerators
        .entrySet()) {

      final GeneratorSettings generatorSettings = entry.getKey();
      System.out.println("URGENCY: " + generatorSettings.urgency + " "
          + generatorSettings.timeSeriesType);

      if (generatorSettings.timeSeriesType == TimeSeriesType.SINE) {
        createScenarios(rng, generatorSettings, entry.getValue(), .0, .46, 10);
      } else if (generatorSettings.timeSeriesType == TimeSeriesType.HOMOGENOUS) {
        createScenarios(rng, generatorSettings, entry.getValue(), .49, .56, 2);
      } else if (generatorSettings.timeSeriesType == TimeSeriesType.NORMAL) {
        createScenarios(rng, generatorSettings, entry.getValue(), .59, .66, 2);
      } else if (generatorSettings.timeSeriesType == TimeSeriesType.UNIFORM) {
        createScenarios(rng, generatorSettings, entry.getValue(), .69, 1, 7);
      } else {
        throw new IllegalArgumentException();
      }
    }
    System.out.println("DONE.");
  }

  static void createScenarios(RandomGenerator rng,
      GeneratorSettings generatorSettings, ScenarioGenerator generator,
      double dynLb, double dynUb, int levels) {
    final List<Scenario> scenarios = newArrayList();

    final Multimap<Double, Scenario> dynamismScenariosMap = LinkedHashMultimap
        .create();
    while (scenarios.size() < levels * TARGET_NUM_INSTANCES) {
      final Scenario scen = generator.generate(rng, "temp");
      Metrics.checkTimeWindowStrictness(scen);
      final StatisticalSummary urgency = Metrics.measureUrgency(scen);

      final long expectedUrgency = generatorSettings.urgency * 60000L;
      if (Math.abs(urgency.getMean() - expectedUrgency) < 0.01
          && urgency.getStandardDeviation() < 0.01) {

        final int numParcels = Metrics.getEventTypeCounts(scen).count(
            PDPScenarioEvent.ADD_PARCEL);
        if (numParcels == NUM_ORDERS) {

          final double dynamism = Metrics.measureDynamism(scen,
              generatorSettings.officeHours);
          System.out.print(String.format("%1.3f ", dynamism));
          if ((dynamism % DYN_STEP_SIZE < DYN_BANDWIDTH || dynamism
              % DYN_STEP_SIZE > DYN_STEP_SIZE - DYN_BANDWIDTH)
              && dynamism <= dynUb && dynamism >= dynLb) {

            final double targetDyn = Math.round(dynamism / DYN_STEP_SIZE)
                * DYN_STEP_SIZE;

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
                final Scenario finalScenario = Scenario.builder(pc)
                    .copyProperties(scen)
                    .problemClass(pc)
                    .instanceId(instanceId)
                    .build();

                ScenarioIO.write(finalScenario,
                    new File(fileName + ".scen").toPath());
              } catch (final IOException e) {
                throw new IllegalStateException(e);
              }
              scenarios.add(scen);
            }
          }

        }
      }
    }
  }

  static void writePropertiesFile(Scenario scen, StatisticalSummary urgency,
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
                .announceTimes(
                    TimeSeries.filter(tsg, TimeSeries.numEvents(NUM_ORDERS)))
                .pickupDurations(constant(PICKUP_DURATION))
                .deliveryDurations(constant(DELIVERY_DURATION))
                .neededCapacities(constant(0))
                .locations(Locations.builder()
                    .min(0d)
                    .max(AREA_WIDTH)
                    .uniform())
                .timeWindows(new CustomTimeWindowGenerator(urgency)
                // TimeWindows.builder()
                // .pickupUrgency(constant(urgency))
                // // .pickupTimeWindowLength(StochasticSuppliers.uniformLong(5
                // // * 60 * 1000L,))
                // .deliveryOpening(constant(0L))
                // .minDeliveryLength(constant(10 * 60 * 1000L))
                // .deliveryLengthFactor(constant(3d))
                // .build()
                )
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

  static class CustomTimeWindowGenerator implements TimeWindowGenerator {
    private static final long MINIMAL_PICKUP_TW_LENGTH = 10 * 60 * 1000L;
    private static final long MINIMAL_DELIVERY_TW_LENGTH = 10 * 60 * 1000L;

    private final long urgency;
    private final StochasticSupplier<Double> pickupTWopening;
    private final StochasticSupplier<Double> deliveryTWlength;
    private final StochasticSupplier<Double> deliveryTWopening;
    private final RandomGenerator rng;

    public CustomTimeWindowGenerator(long urg) {
      urgency = urg;
      pickupTWopening = StochasticSuppliers.uniformDouble(0d, 1d);
      deliveryTWlength = StochasticSuppliers.uniformDouble(0d, 1d);
      deliveryTWopening = StochasticSuppliers.uniformDouble(0d, 1d);
      rng = new MersenneTwister();
    }

    @Override
    public void generate(long seed, Builder parcelBuilder,
        TravelTimes travelTimes, long endTime) {
      rng.setSeed(seed);
      final long orderAnnounceTime = parcelBuilder.getOrderAnnounceTime();
      final Point pickup = parcelBuilder.getPickupLocation();
      final Point delivery = parcelBuilder.getDeliveryLocation();

      final long pickupToDeliveryTT = travelTimes.getShortestTravelTime(pickup,
          delivery);
      final long deliveryToDepotTT = travelTimes
          .getTravelTimeToNearestDepot(delivery);

      // compute range of possible openings
      long pickupOpening;
      if (urgency > MINIMAL_PICKUP_TW_LENGTH) {

        // possible values range from 0 .. n
        // where n = urgency - MINIMAL_PICKUP_TW_LENGTH
        pickupOpening = orderAnnounceTime + DoubleMath.roundToLong(
            pickupTWopening.get(rng.nextLong())
                * (urgency - MINIMAL_PICKUP_TW_LENGTH), RoundingMode.HALF_UP);
      } else {
        pickupOpening = orderAnnounceTime;
      }
      final TimeWindow pickupTW = new TimeWindow(pickupOpening,
          orderAnnounceTime + urgency);
      parcelBuilder.pickupTimeWindow(pickupTW);

      // find boundaries
      final long minDeliveryOpening = pickupTW.begin
          + parcelBuilder.getPickupDuration() + pickupToDeliveryTT;

      final long maxDeliveryClosing = endTime - deliveryToDepotTT
          - parcelBuilder.getDeliveryDuration();
      long maxDeliveryOpening = maxDeliveryClosing - MINIMAL_DELIVERY_TW_LENGTH;
      if (maxDeliveryOpening < minDeliveryOpening) {
        maxDeliveryOpening = minDeliveryOpening;
      }

      final double openingRange = maxDeliveryOpening - minDeliveryOpening;
      final long deliveryOpening = minDeliveryOpening
          + DoubleMath.roundToLong(deliveryTWopening.get(rng.nextLong())
              * openingRange, RoundingMode.HALF_DOWN);

      final long minDeliveryClosing = Math.min(Math.max(pickupTW.end
          + parcelBuilder.getPickupDuration() + pickupToDeliveryTT,
          deliveryOpening + MINIMAL_DELIVERY_TW_LENGTH), maxDeliveryClosing);

      final double closingRange = maxDeliveryClosing - minDeliveryClosing;
      final long deliveryClosing = minDeliveryClosing
          + DoubleMath.roundToLong(deliveryTWlength.get(rng.nextLong())
              * closingRange, RoundingMode.HALF_DOWN);

      final long latestDelivery = endTime - deliveryToDepotTT
          - parcelBuilder.getDeliveryDuration();

      // final long minDeliveryTWlength = MINIMAL_DELIVERY_TW_LENGTH;
      // // Math
      // // .max(MINIMAL_DELIVERY_TW_LENGTH,
      // // pickupTW.end + parcelBuilder.getPickupDuration()
      // // + pickupToDeliveryTT);
      // final long maxDeliveryTWlength = latestDelivery - minDeliveryOpening;
      //
      // double factor = maxDeliveryTWlength - minDeliveryTWlength;
      // if (factor < 0d) {
      // factor = 0;
      // }
      // long deliveryTimeWindowLength = minDeliveryTWlength
      // + DoubleMath.roundToLong(deliveryTWlength.get(rng.nextLong())
      // * factor, RoundingMode.HALF_UP);
      //
      // // delivery TW may not close before this time:
      // final long minDeliveryClosing = pickupTW.end
      // + parcelBuilder.getPickupDuration() + pickupToDeliveryTT;
      //
      // if (minDeliveryOpening < minDeliveryClosing - deliveryTimeWindowLength)
      // {
      // minDeliveryOpening = minDeliveryClosing - deliveryTimeWindowLength;
      // }
      //
      // final long deliveryOpening;
      // if (deliveryTimeWindowLength >= maxDeliveryTWlength) {
      // deliveryOpening = minDeliveryOpening;
      // deliveryTimeWindowLength = maxDeliveryTWlength;
      // } else {
      // deliveryOpening = minDeliveryOpening
      // + DoubleMath.roundToLong(deliveryTWopening.get(rng.nextLong())
      // * (maxDeliveryTWlength - deliveryTimeWindowLength),
      // RoundingMode.HALF_UP);
      // }
      //
      // if (deliveryOpening + deliveryTimeWindowLength > latestDelivery) {
      // deliveryTimeWindowLength = latestDelivery - deliveryOpening;
      // }

      final TimeWindow deliveryTW = new TimeWindow(deliveryOpening,
          deliveryClosing);

      checkArgument(deliveryOpening >= minDeliveryOpening);
      checkArgument(deliveryOpening + deliveryTW.length() <= latestDelivery);
      checkArgument(pickupTW.end + parcelBuilder.getPickupDuration()
          + pickupToDeliveryTT <= deliveryOpening + deliveryTW.length());

      parcelBuilder.deliveryTimeWindow(deliveryTW);
    }
  }
}
