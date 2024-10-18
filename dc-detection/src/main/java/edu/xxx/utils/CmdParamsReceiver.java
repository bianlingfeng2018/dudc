package edu.xxx.utils;

import lombok.ToString;
import picocli.CommandLine.Option;

@ToString
public class CmdParamsReceiver {

  @Option(names = "-ds_id", description = "The dataset id")
  public int dsId;

  @Option(names = "-dc_path", description = "The DCs path")
  public String dcPath;

  @Option(names = {"-h", "--help"}, usageHelp = true, description = "display a help message")
  public boolean helpRequested = false;
}