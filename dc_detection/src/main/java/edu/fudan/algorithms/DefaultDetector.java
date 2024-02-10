package edu.fudan.algorithms;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Lingfeng
 */
@Slf4j
public class DefaultDetector {
  private int dsIndex = -1;
  public DefaultDetector(int dsIndex) {
    this.dsIndex = dsIndex;
  }

  public void detect() {
    log.info("Detecting...");
  }
}
