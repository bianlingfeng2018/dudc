package edu.fudan.utils;

import static edu.fudan.conf.DefaultConf.defaultErrorThreshold;
import static edu.fudan.conf.DefaultConf.maxCellQuestionBudget;
import static edu.fudan.conf.DefaultConf.maxDCQuestionBudget;
import static edu.fudan.conf.DefaultConf.maxDiscoveryRound;
import static edu.fudan.conf.DefaultConf.maxInCluster;
import static edu.fudan.conf.DefaultConf.maxTupleQuestionBudget;
import static edu.fudan.conf.DefaultConf.questionsConf;
import static edu.fudan.conf.DefaultConf.topK;
import static edu.fudan.conf.DefaultConf.topKOfCluster;

import edu.fudan.algorithms.uguide.Evaluation.EvalResult;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class CSVWriter {

  private static final String COMMA_DELIMITER = ",";
  private static final String NEW_LINE_SEPARATOR = "\n";

  public static void writeToFile(String fileName, List<EvalResult> evalResults) {
    try (FileWriter writer = new FileWriter(fileName)) {
      String confStr = String.format(
          "topK=%s\nmaxDiscoveryRound=%s\nmaxCellQuestionBudget=%s\nmaxTupleQuestionBudget=%s\nmaxDCQuestionBudget=%s\ntopKOfCluster=%s\nmaxInCluster=%s\ndefaultErrorThreshold=%s\nquestionsConf=[%s;%s;%s]\n",
          topK, maxDiscoveryRound, maxCellQuestionBudget, maxTupleQuestionBudget,
          maxDCQuestionBudget, topKOfCluster, maxInCluster, defaultErrorThreshold,
          questionsConf[0], questionsConf[1], questionsConf[2]);
      String header = "Round,TrueVios,CandiVios,GTVios,TrueDCs,CandiDCs,GTDCs,CellsOfTrueVios,CellsOfTrueViosAndChanges,CellsOfChanges,CellQuestions,TupleQuestions,DCQuestions,excludedLines,excludedLinesOfCellQ,excludedLinesOfTupleQ,excludedLinesOfDCsQ,errorLinesInSample,errorLinesInSampleAndExcluded\n";
      writer.append(confStr);
      writer.append(header);
      for (int i = 0; i < evalResults.size(); i++) {
        EvalResult res = evalResults.get(i);
        writer.append(String.valueOf(i + 1));
        writer.append(COMMA_DELIMITER);
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
