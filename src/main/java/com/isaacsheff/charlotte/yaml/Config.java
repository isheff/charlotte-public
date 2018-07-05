package com.isaacsheff.charlotte.yaml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class Config extends Contact {
  private static final Logger logger = Logger.getLogger(Config.class.getName());

  private final JsonConfig jsonConfig;
  private final byte[] privateKeyBytes;
  private final ConcurrentMap<String, Contact> contacts;

  public Config(JsonConfig jsonConfig, Path path) {
    super(warnIfNoMe(jsonConfig.getContacts().get(jsonConfig.getMe())), path);
    this.jsonConfig = jsonConfig;
    this.privateKeyBytes = readFile("Private Key File", path.resolve(getPrivateKeyFileName()));
    contacts = new ConcurrentHashMap<String, Contact>(getJsonContacts().size());
    for (Map.Entry<String, JsonContact> entry : getJsonContacts().entrySet()) {
      contacts.putIfAbsent(entry.getKey(), new Contact(entry.getValue(), path));
    }
  }

  public Config(Path path) {
    this(parseYaml(path), path.getParent());
  }

  public Config(String configFileName) {
    this(Paths.get(configFileName));
  }

  public JsonConfig getJsonConfig() {return jsonConfig;}
  public String getMe() {return getJsonConfig().getMe();}
  public String getPrivateKeyFileName() {return getJsonConfig().getPrivateKey();}
  public byte[] getPrivateKeyBytes() {return privateKeyBytes;}
  public ByteArrayInputStream getPrivateKeyStream() {return (new ByteArrayInputStream(getPrivateKeyBytes()));}
  public Map<String, JsonContact> getJsonContacts() {return getJsonConfig().getContacts();}
  public ConcurrentMap<String, Contact> getContacts() {return contacts;}


  private static JsonConfig parseYaml(Path path) {
    try {
      return (new ObjectMapper(new YAMLFactory())).readValue(path.toFile(), JsonConfig.class);
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Something went wrong while reading the YAML config file: " + path, e);
    }
    return null;
  }

  private static JsonContact warnIfNoMe(JsonContact me) {
    if (me == null) {
      logger.log(Level.WARNING, "This config file does not have a contact entry for ME. Things may break.");
    }
    return me;
  }
}
