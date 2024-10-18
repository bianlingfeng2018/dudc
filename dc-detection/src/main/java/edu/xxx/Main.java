//package edu.xxx;
//
//import edu.xxx.utils.CmdParamsReceiver;
//import lombok.extern.slf4j.Slf4j;
//import picocli.CommandLine;
//
///**
// * @author XXX
// */
//@Slf4j
//public class Main {
//
//  public static void main(String[] args) {
//    CmdParamsReceiver params = new CmdParamsReceiver();
//    new CommandLine(params).parseArgs(args);
//    log.info("CmdParams : {}", params.toString());
//
//    if (params.helpRequested) {
//      System.out.println("DC Detection Tool usage: TBD...");
//      return;
//    }
//    int dsId = params.dsId;
//    String dcPath = params.dcPath;
//
//    log.info("Detect violations, DCs={}, dataset={}", dcPath, dsId);
//  }
//
//
//}
