package com.isaacsheff.charlotte.yaml;

import java.util.Map;

public class User {
  private String name;
  private int age;
  private Map<String, Address> addresses;
  private String[] roles;
  public String getName() {
    return name;
  }
  public void setName(String name) {
    this.name = name;
  }
  public int getAge() {
    return age;
  }
  public void setAge(int age) {
    this.age = age;
  }
  public Map<String, Address> getAddresses() {
    return addresses;
  }
  public void setAddresses(Map<String, Address> addresses) {
    this.addresses = addresses;
  }
  public String[] getRoles() {
    return roles;
  }
  public void setRoles(String[] roles) {
    this.roles = roles;
  }
}
