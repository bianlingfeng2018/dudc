package edu.fudan.utils;

import static edu.fudan.conf.DefaultConf.defaultErrorThreshold;
import static edu.fudan.conf.DefaultConf.maxCellQuestionBudget;
import static edu.fudan.conf.DefaultConf.maxDCQuestionBudget;
import static edu.fudan.conf.DefaultConf.maxDiscoveryRound;
import static edu.fudan.conf.DefaultConf.maxInCluster;
import static edu.fudan.conf.DefaultConf.maxTupleQuestionBudget;
import static edu.fudan.conf.DefaultConf.topK;
import static edu.fudan.conf.DefaultConf.topKOfCluster;

import edu.fudan.algorithms.uguide.Evaluation.EvalResult;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class CSVWriter {

  private static final String COMMA_DELIMITER = ",";
  private static final String NEW_LINE_SEPARATOR = "\n";

  public static void writeToFile(String fileName, List<EvalResult> evalResults) {
    try (FileWriter writer = new FileWriter(fileName)) {
      String confStr = String.format(
          "topK=%s;maxDiscoveryRound=%s;maxCellQuestionBudget=%s;maxTupleQuestionBudget=%s;maxDCQuestionBudget=%s;topKOfCluster=%s;maxInCluster=%s;defaultErrorThreshold=%s",
          topK, maxDiscoveryRound, maxCellQuestionBudget, maxTupleQuestionBudget,
          maxDCQuestionBudget, topKOfCluster, maxInCluster, defaultErrorThreshold);
      writer.append(confStr);
      writer.append(NEW_LINE_SEPARATOR);
      for (EvalResult res : evalResults) {
        writer.append(String.valueOf(res.getViolationsTrue()));
        writer.append(COMMA_DELIMITER);
        writer.append(String.valueOf(res.getViolationsCandidate()));
        writer.append(COMMA_DELIMITER);
        writer.append(String.valueOf(res.getViolationsGroundTruth()));
        writer.append(COMMA_DELIMITER);
        writer.append(String.valueOf(res.getDCsTrue()));
        writer.append(COMMA_DELIMITER);
        writer.append(String.valueOf(res.getDCsCandidate()));
        writer.append(COMMA_DELIMITER);
        writer.append(String.valueOf(res.getDCsGroundTruth()));
        writer.append(COMMA_DELIMITER);
        writer.append(String.valueOf(res.getCellsOfTrueVios()));
        writer.append(COMMA_DELIMITER);
        writer.append(String.valueOf(res.getCellsOfTrueViosAndChanges()));
        writer.append(COMMA_DELIMITER);
        writer.append(String.valueOf(res.getCellsOfChanges()));
        writer.append(COMMA_DELIMITER);
        writer.append(String.valueOf(res.getQuestionsCell()));
        writer.append(COMMA_DELIMITER);
        writer.append(String.valueOf(res.getQuestionsTuple()));
        writer.append(COMMA_DELIMITER);
        writer.append(String.valueOf(res.getQuestionsDC()));
        writer.append(COMMA_DELIMITER);

        writer.append(String.valueOf(res.getExcludedLines()));
        writer.append(COMMA_DELIMITER);
        writer.append(String.valueOf(res.getExcludedLinesOfCellQ()));
        writer.append(COMMA_DELIMITER);
        writer.append(String.valueOf(res.getExcludedLinesOfTupleQ()));
        writer.append(COMMA_DELIMITER);
        writer.append(String.valueOf(res.getExcludedLinesOfDCsQ()));
        writer.append(COMMA_DELIMITER);

        writer.append(String.valueOf(res.getErrorLinesInSample()));
        writer.append(COMMA_DELIMITER);
        writer.append(String.valueOf(res.getErrorLinesInSampleAndExcluded()));
        writer.append(NEW_LINE_SEPARATOR);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
