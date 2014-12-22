# On dynamism and urgency
Accompanying webpage for

 > *On dynamism and urgency*, R.R.S. van Lon, E. Ferrante, A.E. Turgut, T. Wenseleers, G. Vanden Berghe, T. Holvoet, European Journal of Operational Research, 2015 (submitted).

For the final version of the paper this webpage and repository will be archived using [zenodo.org](https://zenodo.org/). Consequently, all code and data of the experiments will be accessible via a [DOI](http://www.doi.org/)  ensuring the availability of all resources for the future.

## Overview
Since the paper is part of a long term research effort, the code used for the experiments is distributed over several repositories. The code in the current repository is the glue that instantiates and binds the code from the other repositories to create a cohesive experiment setup.

### In this repository

##### Main experiment

The code of the main experiment sits in the following files:
 + Code that generates the scenarios used in the experiments: [generator](src/main/java/com/github/rinde/dynurg/Generator.java).
 + The scenarios that were generated can be found here __scenarios will be published after acceptance__.
 + Code for performing the experiment: [experiment](src/main/java/com/github/rinde/dynurg/Experimentation.java).
 + The raw results of the experiments can be found here __raw results will be published after acceptance__.

##### Dynamism generation experiment
This is the experiment to find with what parameters and methods certain dynamism levels can be obtained.
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



