# In logistics dynamism and urgency are distinct concepts
Accompanying webpage for

 > *In logistics dynamism and urgency are distinct concepts*, R.R.S. van Lon, E. Ferrante, A.E. Turgut, T. Wenseleers, G. Vanden Berghe, T. Holvoet, European Journal of Operational Research, 2015 (submitted).

## Main experiment
The code of the main experiment sits in the following two files:
 + Code that generates the scenarios used in the experiments: [generator](src/main/java/com/github/rinde/dynurg/Generator.java)
 + The scenarios that were generated can be found here __add link__
 + Code for performing the experiment: [experiment](src/main/java/com/github/rinde/dynurg/Experimentation.java)
 + The raw results of the experiments can be found here __add link__


## Dynamism generation experiment
This is the experiment to find with what parameters and methods certain dynamism levels can be obtained.
 + Code for performing the experiment can be found [here]([experiment](src/main/java/com/github/rinde/dynurg/Experimentation.java))

## Dependencies
 + The optimization algorithms used in the experiment are in a different project: [RinLog](http://github.com/rinde/RinLog) version 1.0.0. 
 + Most of the code for generating scenarios is part of [RinSim](http://github.com/rinde/RinSim) version 3.0.0.

## How to run

 + download this project (or git clone)
 + make sure Maven is installed



