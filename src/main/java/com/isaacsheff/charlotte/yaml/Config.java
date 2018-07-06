package com.isaacsheff.charlotte.yaml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class Config extends Contact {
  private static final Logger logger = Logger.getLogger(Config.class.getName());

  private final JsonConfig jsonConfig;
  private final byte[] privateKeyBytes;
  private final ConcurrentMap<String, Contact> contacts;
  private final ConcurrentMap<String, ConcurrentMap<Integer, Contact>> contactsByUrl;
  private final KeyPair keyPair;

  public Config(JsonConfig jsonConfig, Path path) {
    super(warnIfNoMe(jsonConfig.getContacts().get(jsonConfig.getMe())), path);
    this.jsonConfig = jsonConfig;
    privateKeyBytes = readFile("Private Key File", path.resolve(getPrivateKeyFileName()));
    contacts = new ConcurrentHashMap<String, Contact>(getJsonContacts().size());
    contactsByUrl = new ConcurrentHashMap<String, ConcurrentMap<Integer, Contact>>();
    for (Map.Entry<String, JsonContact> entry : getJsonContacts().entrySet()) {
      contactsByUrl.putIfAbsent(entry.getValue().getUrl(), new ConcurrentHashMap<Integer, Contact>())
                   .putIfAbsent(entry.getValue().getPort(),
                       contacts.putIfAbsent(entry.getKey(), new Contact(entry.getValue(), path)));
    }
    keyPair = new KeyPair(getPublicKey(), generatePrivateKey());
    // TODO: maybe test the validity of this key pair?
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
  public InputStreamReader getPrivateKeyReader() {return new InputStreamReader(getPrivateKeyStream());}
  public Map<String, JsonContact> getJsonContacts() {return getJsonConfig().getContacts();}
  public ConcurrentMap<String, Contact> getContacts() {return contacts;}
  public Contact getContact(String name) {return getContacts().get(name);}
  public ConcurrentMap<String, ConcurrentMap<Integer, Contact>> getContactsByUrl() {return contactsByUrl;}
  public ConcurrentMap<Integer, Contact> getContactsByUrl(String url) {return getContactsByUrl().get(url);}
  public Contact getContactByUrlAndPort(String url, Integer port) {return getContactsByUrl(url).get(port);}
  public KeyPair getKeyPair() {return keyPair;}
  public PrivateKey getPrivateKey() {return getKeyPair().getPrivate();}

  private PrivateKey generatePrivateKey() {
    PrivateKey privateKey = null;
    Reader reader = getPrivateKeyReader();
    PEMParser parser= new PEMParser(reader);
    try {
      Object object = parser.readObject();
      if (!(object instanceof PrivateKeyInfo)) {
      }
      PrivateKeyInfo privateKeyInfo = (PrivateKeyInfo) object;
      JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
      privateKey = converter.getPrivateKey(privateKeyInfo);
    } catch (PEMException e) {
      logger.log(Level.SEVERE, "Private Key file could not be parsed as PEM", e);
    } catch (IOException e) {
      logger.log(Level.SEVERE, "PEM converter could not pull a PrivateKeyInfo from the Private Key File", e);
    }
    try {
      parser.close();
    } catch (IOException e) {
      logger.log(Level.WARNING,
          "PEM parser did not close properly, but we parsed everything, so it's probably ok.", e);
    }
    try {
      reader.close();
    } catch (IOException e) {
      logger.log(Level.WARNING,
        "Private Key byte[] reader didn't close properly, but we parsed everything, so it's probably ok.", e);
    }
    return privateKey;
  }


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
