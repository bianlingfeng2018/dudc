package edu.xxx.algorithms;

import static edu.xxx.conf.DefaultConf.activeScript;
import static edu.xxx.conf.DefaultConf.env;
import static edu.xxx.conf.DefaultConf.myScriptDir;
import static edu.xxx.conf.DefaultConf.predictScript;
import static edu.xxx.conf.DefaultConf.trainScript;

import com.google.common.collect.Lists;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PythonCaller {

  public static void trainModel(String[] args2) throws IOException, InterruptedException {
    String[] args1 = {activeScript, env, "&&", "python", trainScript};
    String[] args = mergeTwoArgs(args1, args2);
    execute(args);
  }

  public static void predict(String[] args2) throws IOException, InterruptedException {
    String[] args1 = {activeScript, env, "&&", "python", predictScript};
    String[] args = mergeTwoArgs(args1, args2);
    execute(args);
  }

  private static void execute(String[] args) throws IOException, InterruptedException {
    ProcessBuilder pb = new ProcessBuilder(args);
    List<String> command = pb.command();
    log.info("Execute command: {}", command);
    // 设置 Python 解释器的工作目录
    pb.directory(new File(myScriptDir));
    pb.redirectErrorStream(true);
    Process process = pb.start();

    // 注册关闭钩子
    // TODO: 强制结束java程序后，python程序并未关闭，还在继续运行，如何解决？
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        // 终止子进程
        if (process != null) {
          process.destroy();
        }
      }
    });

    // 读取Python脚本的输出
    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
    String line;
    while ((line = reader.readLine()) != null) {
      log.debug(line);
    }

    // 等待Python脚本执行完成
    int exitCode = process.waitFor();
    log.debug("Python script exited with code " + exitCode);
  }

  public static String[] mergeTwoArgs(String[] args1, String[] args2) {
    List<String> listA = Lists.newArrayList(args1);
    List<String> listB = Lists.newArrayList(args2);

    List<String> mergedList = Lists.newArrayList();
    mergedList.addAll(listA);
    mergedList.addAll(listB);

    String[] c = mergedList.toArray(new String[0]);
    return c;
  }
}
