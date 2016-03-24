# Measures of dynamism and urgency in logistics
This repository contains the code that was used to perform the experiments described in:

 > *Measures of dynamism and urgency in logistics*. Rinde R.S. van Lon, Eliseo Ferrante, Ali E. Turgut, Tom Wenseleers, Greet Vanden Berghe, and Tom Holvoet.  European Journal of Operational Research (2016). ISSN 0377-2217. http://dx.doi.org/10.1016/j.ejor.2016.03.021.

## Overview
Since the paper is part of a long term research effort, the code used for the experiments is distributed over several repositories. The code in the current repository is the glue that instantiates and binds the code from the other repositories to create a cohesive experiment setup.

### In this repository

##### Main experiment

The code of the main experiment sits in the following files:

 1. Scenario files
   + Code that generates the scenarios used in the experiments: [generator](src/main/java/com/github/rinde/dynurg/Generator.java).
   + The scenarios that were generated can be found here [![DOI](https://zenodo.org/badge/doi/10.5281/zenodo.48217.svg)](http://dx.doi.org/10.5281/zenodo.48217)
.

 1. Experiment results
   + Code for performing the experiment: [experiment](src/main/java/com/github/rinde/dynurg/Experimentation.java).
   + The raw results of the experiments can be found here [![DOI](https://zenodo.org/badge/doi/10.5281/zenodo.48217.svg)](http://dx.doi.org/10.5281/zenodo.48217)
.

 1. Analysis

   For doing the model selection based on the Akaike Information Criterion we have used the following two R-scripts. These scripts require the results from the previous step in ```files/results```.
   + [script for cheapest insertion heuristic](files/multipleRegressionCentral-Solver-CheapInsert.R)
   + [script for 2-opt heuristic](files/multipleRegressionCentral-Solver-bfsOpt2-CheapInsert.R)
  

##### Dynamism generation experiment
This is the experiment for finding the parameters and methods to generate the different dynamism levels.
 + Code for performing the experiment can be found [here]([experiment](src/main/java/com/github/rinde/dynurg/Experimentation.java)).

##### Dynamism computation example
Code for computing dynamism of two example scenarios (as described in the appendix of the paper) can be found [here](src/main/java/com/github/rinde/dynurg/DynamismComputationExample.java).

### Dependencies
 + The optimization algorithms used in the experiment are part of [RinLog](http://github.com/rinde/RinLog) version 1.0.0. [![DOI](https://zenodo.org/badge/7417/rinde/RinLog.svg)](http://dx.doi.org/10.5281/zenodo.13344)
 + Most of the code for generating scenarios is part of [RinSim](http://github.com/rinde/RinSim) version 3.0.0. [![DOI](https://zenodo.org/badge/7417/rinde/RinSim.svg)](http://dx.doi.org/10.5281/zenodo.13343)

### How to run
 + Git clone this project.
 + Make sure Eclipse with Maven is installed (Java 7). Import the project into Eclipse, all dependencies are automatically loaded by Maven (if not, you can download them manually via the links above).
 + Run one of the Java files described on this page (simply click ```Run As``` -> ```Java Application```.
 	+ Note that for running the main experiment the dataset must be available locally (either download it or generate anew). The main experiment is very computational intensive, prepare for a long wait or parallelize over many computers (we used more than 80 modern PCs simultaneously).



