package com.isaacsheff.charlotte.node;

import org.bouncycastle.jcajce.provider.digest.SHA3;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;

import java.security.Security;

/**
 * Small utility functions for taking sha3 hashes of stuff
 * @author Isaac Sheff
 */
public class HashUtil {
  /**
   * This line is required to use bouncycastle encryption libraries.
   */
  static {Security.addProvider(new BouncyCastleProvider());}

  /**
   * @param bytes the bytes you want to hash
   * @return the sha3 hash of those bytes
   */
  public static byte[] sha3(byte[] bytes) {return (new SHA3.Digest256()).digest(bytes);}

  /**
   * @param byteString the bytes you want to hash
   * @return the sha3 hash of those bytes
   */
  public static byte[] sha3(ByteString byteString) {return sha3(byteString.toByteArray());}

  /**
   * @param message the message you want to hash
   * @return the sha3 hash of that message, as bytes
   */
  public static byte[] sha3(MessageLite message) {return sha3(message.toByteArray());}
}
