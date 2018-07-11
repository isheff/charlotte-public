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

/**
 * Represents a fully read / parsed / etc Configuration file for a Charlotte Node.
 * This wraps a JsonConfig object, which is just the literal contents of the config file.
 * The Config object, in contrast, has read and parsed all the private and public key files.
 * This also is an instance of a Contact object, specifically the Contact object for this Charlotte Node.
 * @author Isaac Sheff
 */
public class Config extends Contact {
  /**
   * Used for logging any events of interest that happen in the Config object.
   */
  private static final Logger logger = Logger.getLogger(Config.class.getName());

  /**
   * The literal parsed contents of the config file
   */
  private final JsonConfig jsonConfig;

  /**
   * The raw bytes of the PEM private key file named in the config file
   */
  private final byte[] privateKeyBytes;

  /**
   * A map from string "names" to Contact objects for each participant in the system
   */
  private final ConcurrentMap<String, Contact> contacts;

  /**
   * A map from URLs (or IP strings) to (a map from port numbers to Contact objects for each participant)
   */
  private final ConcurrentMap<String, ConcurrentMap<Integer, Contact>> contactsByUrl;

  /**
   * The private / public crypto keys for this CharlotteNode
   */
  private final KeyPair keyPair;

  /**
   * Read and parse all the key files given a JsonConfig object.
   * This will also log Warnings if no "contact" in the config file matches the name given for "me."
   * @param jsonConfig the literal parsed contents of the config file
   * @param path the directory in which the config file resides (key filenames are relative to this)
   */
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

  /**
   * Read and parse the Config file, and all the key files to create a new Config object.
   * This will log any problems with parsing the JsonConfig from the config file.
   * This will also log Warnings if no "contact" in the config file matches the name given for "me."
   * @param path the Config Filename
   */
  public Config(Path path) {
    this(parseYaml(path), path.getParent());
  }

  /**
   * Read and parse the Config file, and all the key files to create a new Config object.
   * This will log any problems with parsing the JsonConfig from the config file.
   * This will also log Warnings if no "contact" in the config file matches the name given for "me."
   * @param configFileName the Config Filename
   */
  public Config(String configFileName) {
    this(Paths.get(configFileName));
  }

  /**
   * @return The literal parsed contents of the config file
   */
  public JsonConfig getJsonConfig() {return jsonConfig;}

  /**
   * @return The String name identifying the contact in the config file representing this CharlotteNode
   */
  public String getMe() {return getJsonConfig().getMe();}

  /**
   * @return the filename (relative to the config file) of the private key PEM file
   */
  public String getPrivateKeyFileName() {return getJsonConfig().getPrivateKey();}

  /**
   * @return the bytes of the private key PEM file
   */
  public byte[] getPrivateKeyBytes() {return privateKeyBytes;}

  /**
   * @return A new InputStream which inputs the bytes of the private key PEM file
   */
  public ByteArrayInputStream getPrivateKeyStream() {return (new ByteArrayInputStream(getPrivateKeyBytes()));}

  /**
   * @return A new Reader which reads the bytes of the private key PEM file
   */
  public InputStreamReader getPrivateKeyReader() {return new InputStreamReader(getPrivateKeyStream());}

  /**
   * @return the map of String Names to contacts parsed from the config file
   */
  public Map<String, JsonContact> getJsonContacts() {return getJsonConfig().getContacts();}

  /**
   * @return A map from string "names" to Contact objects for each participant in the system
   */
  public ConcurrentMap<String, Contact> getContacts() {return contacts;}

  /**
   * @param name the String name of the desired Contact object
   * @return the Contact object for the given name, if one exists, null otherwise
   */
  public Contact getContact(String name) {return getContacts().get(name);}

  /**
   * @return map from URLs (or IP strings) to (map from port numbers to Contact objects for each participant)
   */
  public ConcurrentMap<String, ConcurrentMap<Integer, Contact>> getContactsByUrl() {return contactsByUrl;}

  /**
   * Given a URL, if no known Contacts have that URL, return null, otherwise a map from port to contact.
   * @param url the URL for Contacts desired
   * @return for the url given, the map from port numbers to Contact objects for each participant (or null)
   */
  public ConcurrentMap<Integer, Contact> getContactsByUrl(String url) {return getContactsByUrl().get(url);}

  /**
   * Given a URL and port, return the Contact known, or null (if none known).
   * Warning: probably throws an error if the URL is not known.
   * @param url the URL for Contact desired
   * @param port the port of the Contact Desired
   * @return for Contact with that URL and port, or null (if none known).
   */
  public Contact getContactByUrlAndPort(String url, Integer port) {return getContactsByUrl(url).get(port);}

  /**
   * @return The private / public crypto keys for this CharlotteNode
   */
  public KeyPair getKeyPair() {return keyPair;}

  /**
   * @return The private crypto key for this CharlotteNode
   */
  public PrivateKey getPrivateKey() {return getKeyPair().getPrivate();}

  /**
   * Utility method.
   * The constructor calls this.
   * Logs Warnings or Severes if the private key can't be parsed right.
   * @return the PrivateKey generated from the PEM file bytes we read from the private key file
   */
  private PrivateKey generatePrivateKey() {
    PrivateKey privateKey = null;
    Reader reader = getPrivateKeyReader();
    PEMParser parser= new PEMParser(reader);
    try {
      Object object = parser.readObject();
      if (!(object instanceof PrivateKeyInfo)) {
        logger.log(Level.SEVERE, "Private Key file parsed as a non-PEM object");
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

  /**
   * Parse a config file into a JsonConfig object.
   * @param path the location of the config file
   * @return the parsed JsonConfig object
   */
  private static JsonConfig parseYaml(Path path) {
    try {
      return (new ObjectMapper(new YAMLFactory())).readValue(path.toFile(), JsonConfig.class);
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Something went wrong while reading the YAML config file: " + path, e);
    }
    return null;
  }

  /**
   * Really just logs a warning if the given object is null, and then returns whatever it's given.
   * @param me the JsonContact hopefully representing this server (logs a warning if it's null)
   * @return whatever was input
   */
  private static JsonContact warnIfNoMe(JsonContact me) {
    if (me == null) {
      logger.log(Level.WARNING,
        "This config file does not have a contact entry for \"me\". Things may break.");
    }
    return me;
  }
}
