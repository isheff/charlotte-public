package com.isaacsheff.charlotte.collections;

import java.util.Map;

/**
 * A Map featuring a blockingGet method, which returns when the Map has a value assiciated with the given key.
 */
public interface BlockingMap<K,V> extends Map<K,V> {
  /**
   * Returns the value associated with the given key.
   * If no such value exists yet, it will wait to return until there is one.
   *
   * @param   key The key associated with the desired value
   * @returns     The value associated with that key
   * @see     get
   */
  public V blockingGet(K key);
}
