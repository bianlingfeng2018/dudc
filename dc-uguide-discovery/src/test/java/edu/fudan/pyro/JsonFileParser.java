package edu.fudan.pyro;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// 最外层的 JSON 对象类
class FunctionalDependency {

  private String type;
  private Determinant determinant;
  private Dependant dependant;

  // Getters and Setters
  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Determinant getDeterminant() {
    return determinant;
  }

  public void setDeterminant(Determinant determinant) {
    this.determinant = determinant;
  }

  public Dependant getDependant() {
    return dependant;
  }

  public void setDependant(Dependant dependant) {
    this.dependant = dependant;
  }

  @Override
  public String toString() {
    return "FunctionalDependency{" +
        "type='" + type + '\'' +
        ", determinant=" + determinant +
        ", dependant=" + dependant +
        '}';
  }
}

// 嵌套的 determinant 对象类
class Determinant {

  private List<Dependant> columnIdentifiers;

  public List<Dependant> getColumnIdentifiers() {
    return columnIdentifiers;
  }

  public void setColumnIdentifiers(List<Dependant> columnIdentifiers) {
    this.columnIdentifiers = columnIdentifiers;
  }

  @Override
  public String toString() {
    return "Determinant{" +
        "columnIdentifiers=" + columnIdentifiers +
        '}';
  }
}

// 嵌套的 dependant 对象类
class Dependant {

  private String tableIdentifier;
  private String columnIdentifier;

  public String getTableIdentifier() {
    return tableIdentifier;
  }

  public void setTableIdentifier(String tableIdentifier) {
    this.tableIdentifier = tableIdentifier;
  }

  public String getColumnIdentifier() {
    return columnIdentifier;
  }

  public void setColumnIdentifier(String columnIdentifier) {
    this.columnIdentifier = columnIdentifier;
  }

  @Override
  public String toString() {
    return "Dependant{" +
        "tableIdentifier='" + tableIdentifier + '\'' +
        ", columnIdentifier='" + columnIdentifier + '\'' +
        '}';
  }
}


public class JsonFileParser {

  public static void main(String[] args) {
    Gson gson = new Gson();

    String input =
        "D:\\paper\\dc_user_guided_detection\\experiment\\data\\与FD算法对比\\fds"; // 替换为你的文件路径
    String header =
        "D:\\paper\\dc_user_guided_detection\\experiment\\data\\与FD算法对比\\header.csv"; // 替换为你的文件路径
    // 文件路径
    String out = "D:\\paper\\dc_user_guided_detection\\experiment\\data\\与FD算法对比\\output.txt";

    // 解析表头
    List<String[]> headers = Lists.newArrayList();
    try (BufferedReader br = new BufferedReader(new FileReader(header))) {
      String line = br.readLine();
      headers = parseLine(line);
      for (String[] pair : headers) {
        System.out.println("Name: " + pair[0] + ", Type: " + pair[1]);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    // 解析Pyro结果
    List<String> rules = Lists.newArrayList();
    try (BufferedReader br = new BufferedReader(new FileReader(input))) {
      String line;
      while ((line = br.readLine()) != null) {
        try {
          // 将每一行的 JSON 解析为 YourClass 对象
          FunctionalDependency obj = gson.fromJson(line, FunctionalDependency.class);
//          System.out.println(obj);
          String rule = convertToDCStr(obj, headers);
          rules.add(rule);
        } catch (JsonSyntaxException e) {
          System.err.println("解析错误: " + e.getMessage());
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    // 调用写入方法
    writeListToFile(rules, out);
  }

  private static String convertToDCStr(FunctionalDependency obj, List<String[]> headers) {
    Determinant determinant = obj.getDeterminant();
    Dependant dependant = obj.getDependant();
    List<Dependant> identifiers = determinant.getColumnIdentifiers();
    String res = "not(";
    for (Dependant id : identifiers) {
      String col = getColName(id.getColumnIdentifier(), headers);
      res += "t1." + col + "=" + "t2." + col + "^";
    }
    String col = getColName(dependant.getColumnIdentifier(), headers);
    res += "t1." + col + "!=" + "t2." + col;
    res += ")";
    return res;
  }

  private static String getColName(String col, List<String[]> headers) {
    String col0 = col.replace("column", "");
    int i = Integer.parseInt(col0) - 1;
    String colName = headers.get(i)[0];
    return colName;
  }

  public static void writeListToFile(List<String> list, String filePath) {
    // 使用 try-with-resources 自动关闭资源
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
      // 将 List 中的每个字符串写入文件
      for (String line : list) {
        writer.write(line);
        writer.newLine(); // 换行
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static List<String[]> parseLine(String line) {
    List<String[]> parsedList = new ArrayList<>();

    // 以逗号分隔每个字段
    String[] parts = line.split(",");

    for (String part : parts) {
      // 使用正则表达式提取 name 和 type
      String name = part.substring(0, part.indexOf("("));
      String type = part.substring(part.indexOf("(") + 1, part.indexOf(")"));

      // 将 name 和 type 组成数组并加入列表
      parsedList.add(new String[]{name, type});
    }

    return parsedList;
  }
}
