# Introduction
This repository contains the source code for reproducing the results in our paper titled **Discovering Effective Approximate Denial Constraints With Dynamic Thresholds and User Feedback**.

## Repository Structure
- `dc-base/`: Common utils, global configurations and basic data structure.
- `dc-detection/`: Detect errors using DCs.
- `dc-discovery/`: Discovery DCs.
- `dc-sampling/`: Sample from data.
- `dc-uguide-disccovery/`: Control the iteration, including user feedback and dynamic thresholds.
- `data/`: datasets including: ground truth DCs, correlation matrix(predicted by a pre-trained correlation model), clean and dirty version data.
- `README.md`: Documentation

## Dependencies
- Java >= 1.8
- Maven >= 3.6.3

## Datasets used in our experiments
We use five real-life datasets: Hospital, Stock, Flights, Airport and Adult, along with a large synthetic dataset Tax. Datasets can be download from the `data/` folder.

## How to build
`mvn clean package -DskipTests`

## Running Instructions
1. **Preparation**: Put the `data/` directory and the jar file in the same directory.
2. **Run .jar file on dataset(an example)**: `java -jar dc-uguide-discovery-1.0-SNAPSHOT-jar-with-dependencies.jar -i 2 -r 10 -k 5 -n 3 -len 3 -cq 100 -tq 100 -dq 10 -tk 50 -th 0.0001 -u REPAIR -s EFFICIENT -a HYDRA -c VIO_AND_CONF -t VIOLATIONS_PRIOR -d SUC_COR_VIOS -g DYNAMIC`
3. **Results**: Test results will be saved in the `data/` folder.

## Command Line Parameters
"-i", "--dataset", "The dataset index."

"-r", "--rounds", "Max rounds of iteration."

"-u", "--update", "Update tuples strategy."

"-s", "--sample", "Sample strategy."

"-a", "--discovery", "Approximate DC discovery method."

"-c", "--cell-question", "Cell question strategy."

"-t", "--tuple-question", "Tuple question strategy."

"-d", "--dc-question", "DC question strategy."

"-g", "--g1", "Threshold strategy."

"-k", "--cluster", "Number of clusters."

"-n", "--tuple", "Number of tuples in each cluster."

"-len", "--length", "Max length of a DC."

"-cq", "--cellsq", "Cells question budget."

"-tq", "--tupleq", "Tuples question budget."

"-dq", "--dcq", "DCs question budget."

"-tk", "--top-k", "Top-k discovered DCs."

"-th", "--threshold", "Threshold used for discovering DCs."

## Citation
**To be added after acceptance.**

## License
**To be added after acceptance.**
