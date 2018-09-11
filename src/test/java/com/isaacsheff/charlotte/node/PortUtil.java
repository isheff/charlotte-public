package com.isaacsheff.charlotte.node;

public class PortUtil {
  private static int port = 8001;

  public static int getFreshPort() { return (port++);}

}
