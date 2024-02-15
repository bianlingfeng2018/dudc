package edu.fudan.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * @author Lingfeng
 */
public class FileUtil {

  public static void writeLinesToFile(List<List<String>> lines, File out) throws IOException {
    BufferedWriter bw = new BufferedWriter(new FileWriter(out));
    for (List<String> line : lines) {
      String joined = String.join(",", line);
      bw.write(joined);
      bw.newLine();
    }
    bw.close();
  }

}
