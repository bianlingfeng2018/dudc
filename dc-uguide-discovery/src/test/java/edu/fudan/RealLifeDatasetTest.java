package edu.fudan;

import static edu.fudan.transformat.DCFormatUtil.extractColumnNameType;
import static edu.fudan.utils.FileUtil.generateNewCopy;
import static edu.fudan.utils.FileUtil.writeListLinesToFile;

import de.hpi.naumann.dc.input.Input;
import de.hpi.naumann.dc.input.ParsedColumn;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * @author Lingfeng
 */
public class RealLifeDatasetTest {

  @Test
  public void testGenerateChanges() {
    String cleanPath = "../data/rayyan/preprocessed_rayyan.csv";
    String dirtyPath = "../data/rayyan/preprocessed_rayyan_dirty.csv";
    String outPath = "../data/rayyan/preprocessed_rayyan_changes.csv";
//    String cleanPath = "../data/flights/clean.csv";
//    String dirtyPath = "../data/flights/dirty.csv";
//    String outPath = "../data/flights/changes.csv";

    Input ci = generateNewCopy(cleanPath);
    Input di = generateNewCopy(dirtyPath);

    int lineCount1 = ci.getLineCount();
    int lineCount2 = di.getLineCount();

    if (lineCount1 != lineCount2) {
      throw new RuntimeException("LineCounts do not match");
    }

    ParsedColumn<?>[] columnsClean = ci.getColumns();
    ParsedColumn<?>[] columnsDirty = di.getColumns();

    if (columnsClean.length != columnsDirty.length) {
      throw new RuntimeException("Columns do not match");
    }

    List<List<String>> changes = new ArrayList<>();
    ArrayList<String> heads = new ArrayList<>();
    heads.add("attribute");
    heads.add("wrong_val");
    heads.add("correct_val");
    changes.add(heads);
    for (int i = 0; i < lineCount1; i++) {  // lineCount 从0开始
      int len = columnsClean.length;
      for (int j = 0; j < len; j++) {
        ParsedColumn<?> pcClean = columnsClean[j];
        ParsedColumn<?> pcDirty = columnsDirty[j];

        Comparable<?> vClean = pcClean.getValue(i);
        Comparable<?> vDirty = pcDirty.getValue(i);

        if (vClean == null || vDirty == null) {
          throw new RuntimeException("Value is null");
        }
        if (!vClean.equals(vDirty)) {
          ArrayList<String> change = new ArrayList<>();
          String attrStr = pcClean.getName();
          String[] nameType = extractColumnNameType(attrStr);
          String nameLower = nameType[0].toLowerCase();
          change.add(String.valueOf(i + 1) + "." + nameLower);  // changes从1开始
          change.add(vDirty.toString());
          change.add(vClean.toString());
          changes.add(change);
        }
      }
    }

    writeListLinesToFile(changes, new File(outPath));
  }

}
