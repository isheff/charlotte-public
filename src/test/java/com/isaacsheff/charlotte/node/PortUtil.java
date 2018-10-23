package com.isaacsheff.charlotte.node;

/** A utility for tests to ensure no two test servers use the same port */
public class PortUtil {
  /** the start port (the first port a server will use) **/
  private static int port = 8001;

  /** @return a fresh port that has not been returned before (they increment) */
  public static int getFreshPort() { return (port++);}

}
