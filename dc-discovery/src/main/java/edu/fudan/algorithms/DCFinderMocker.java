package edu.fudan.algorithms;

import static edu.fudan.conf.DefaultConf.minimumSharedValue;
import static edu.fudan.conf.DefaultConf.noCrossColumn;
import static edu.fudan.transformat.DCFormatUtil.extractColumnNameType;
import static edu.fudan.transformat.DCFormatUtil.isLegalIndex4DCString;

import com.google.common.collect.Lists;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import de.metanome.algorithm_integration.input.RelationalInput;
import de.metanome.algorithms.dcfinder.DCFinder;
import de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint;
import de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraintSet;
import de.metanome.algorithms.dcfinder.input.Input;
import de.metanome.algorithms.dcfinder.predicates.PredicateBuilder;
import de.metanome.algorithms.dcfinder.predicates.sets.PredicateSet;
import de.metanome.backend.input.file.DefaultFileInputGenerator;
import edu.fudan.transformat.DCFormatUtil;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Slf4j
public class DCFinderMocker implements Callable<Integer> {

  @Parameters(index = "0", description = "The input file.")
  private String inputFilePath;

  @Option(names = {"-t", "--error_threshold"}, description = "The error threshold, i.e., g1.")
  private double errorThreshold;

//  @Option(names = {"-e", "--evidence_file"}, description = "The evidence file.")
//  private String evidenceFile;

  @Option(names = {"-o", "--output"}, description = "The output file.")
  private String outputFilePath;

  @Override
  public Integer call() throws Exception {
    RelationalInput ri = null;
    Input input;
    try {
      ri = new DefaultFileInputGenerator(new File(this.inputFilePath)).generateNewCopy();
      input = new Input(ri);
    } catch (InputGenerationException | FileNotFoundException | InputIterationException e) {
      throw new RuntimeException(e);
    } finally {
      try {
        if (ri != null) {
          ri.close();
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    PredicateBuilder predicatesSpace = new PredicateBuilder(input, noCrossColumn,
        minimumSharedValue);
    log.info("Size of the predicate space:" + predicatesSpace.getPredicates().size());

    DenialConstraintSet dcs = new DCFinder().run(input, predicatesSpace, this.errorThreshold, null);

    // Persist discovered dcs
    if (this.outputFilePath != null) {
      log.info("Saving DCs into: " + this.outputFilePath);
      BufferedWriter bw = new BufferedWriter(new FileWriter(this.outputFilePath));
      for (DenialConstraint dc : dcs) {
        bw.write(convertDCFinderDC2Str(dc));
        bw.newLine();
      }
      bw.close();
    }

    return 0;
  }

  public String convertDCFinderDC2Str(
      de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint dcFinderDC) {
    List<String> predicates = Lists.newArrayList();
    PredicateSet predicateSet = dcFinderDC.getPredicateSet();
    for (de.metanome.algorithms.dcfinder.predicates.Predicate p : predicateSet) {
      de.metanome.algorithms.dcfinder.predicates.operands.ColumnOperand<?> operand1 = p.getOperand1();
      de.metanome.algorithms.dcfinder.predicates.operands.ColumnOperand<?> operand2 = p.getOperand2();
      int operand1Index = operand1.getIndex() + 1;
      int operand2Index = operand2.getIndex() + 1;
      String col1Name = extractColumnNameType(operand1.getColumn().getName())[0];
      String col2Name = extractColumnNameType(operand2.getColumn().getName())[0];
      String op = DCFormatUtil.convertOperator2String(p.getOperator());
      if (!isLegalIndex4DCString(operand1Index) || !isLegalIndex4DCString(operand2Index)) {
        throw new RuntimeException("Illegal column index for DC string");
      }
      String predicate =
          "t" + operand1Index + "." + col1Name + op + "t" + operand2Index + "." + col2Name;
      predicates.add(predicate);
    }
    String dcStr = "not(" + predicates.stream().sorted().collect(Collectors.joining("^")) + ")";
    return dcStr;
  }
}
