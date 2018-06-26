package com.isaacsheff.charlotte.collections;

import java.util.concurrent.ConcurrentHashMap;

/**
 * A map with a blockingGet function.
 * This is based on ConcurrentHashMap, so it inherits all that stuff.
 * Calling blockingGet is a lot like get, except it waits for a value to exist, if there isn't one yet.
 * @author Isaac Sheff
 */
public class BlockingConcurrentHashMap<K,V> extends ConcurrentHashMap<K,V> implements BlockingMap<K,V> {
  /**
   * I just stuck the date and time in here...
   */
  public static final long serialVersionUID = 201806251816L;

  /** 
   * All the pending requests are kept here.
   * When a new request comes in for something that's not already in the map, they wait on a monitor in here.
   */
  private ConcurrentHashMap<K,ConcurrentHolder<V>> pendingHolders;


  /**
   * get a value associated with the key, even if we have to wait for one to arrive.
   * BLOCKING.
   * @param key the key for which you want an associated value
   * @return the associated value 
   */
  public V blockingGet(K key) {
    V value = get(key);
    if (value != null) {
      return value;
    }
    // It's cleaner to call new below, but maybe it would be faster if we did
    //  (possibly) 2 lookups, and didn't call new if the map was occupied to
    //  begin with.
    ConcurrentHolder<V> newHolder = new ConcurrentHolder<V>(); 
    ConcurrentHolder<V> oldHolder = pendingHolders.putIfAbsent(key, newHolder);
    if (oldHolder == null)
    {
      return newHolder.get();
    }
    return oldHolder.get();
  }

  /**
   * Put a value in the map.
   * Overwrites previous values.
   * Note: In rare circumstances, a the following chain of events can occur:
   * <ul>
   * <li>    put(k, v1) starts                                        </li>
   * <li>      get(k) returns v1                                      </li>
   * <li>      put(k, v2) returns v2                                  </li>
   * <li>      get(k, v2) returns v2                                  </li>
   * <li>      remove(k) returns v2                                   </li>
   * <li>      get(k) returns null                                    </li>
   * <li>      pendingGet(k) returns v1   THIS IS THE ERRONIOUS PART  </li>
   * <li>    put(k,v1) finishes                                       </li>
   * </ul>
   * This could be removed, for instance, by synchronizing this method.
   * Basically, it's messy and inefficient to avoid this, and I don't need to.
   *
   * @param key the associated key
   * @param value the value to be written to that key.
   * @return the value written to that key.
   */
  @Override
  public V put(K key, V value) {
    V v = super.put(key, value);
    fillHolder(key, value);
    return v;
  }

  /**
   * Put a value in the map, iff there isn't one already associated with this key.
   * Note: In rare circumstances, a the following chain of events can occur:
   * <ul>
   * <li>    put(k, v1) starts                                        </li>
   * <li>      get(k) returns v1                                      </li>
   * <li>      put(k, v2) returns v2                                  </li>
   * <li>      get(k, v2) returns v2                                  </li>
   * <li>      remove(k) returns v2                                   </li>
   * <li>      get(k) returns null                                    </li>
   * <li>      pendingGet(k) returns v1   THIS IS THE ERRONIOUS PART  </li>
   * <li>    put(k,v1) finishes                                       </li>
   * </ul>
   * This could be removed, for instance, by synchronizing this method.
   * Basically, it's messy and inefficient to avoid this, and I don't need to.
   * @param key the associated key
   * @param value the value to be written ot that key
   * @return  the value now associated with that key.
   */
  @Override 
  public V putIfAbsent(K key, V value) {
    V v = super.putIfAbsent(key, value);
    fillHolder(key, v);
    return v;
  }

  /**
   * If there are threads waiting to get this value, give it to them.
   * Removes the holder from the set of pending holders, and passes the given value to all waiting threads.
   */
  private void fillHolder(K key, V value) {
    ConcurrentHolder<V> holder = pendingHolders.remove(key);
    if (holder != null) {
      holder.put(value);
    }
  }



  /**
   * This is just a monitor.
   * Get just blocks until the value stored is non-null.
   * Put just fills in the value stored and then calls notifyAll().
   */
  private class ConcurrentHolder<T> {
    /**
     * The value which waiting processes are waiting to read.
     */
    private T value;
  
    /**
     * Processces call this to wait until they can read the value.
     * @return the value you've been waiting for
     */
    public T get() {
      T v;
      synchronized(this) {
        v = this.value;
        while (v == null) {
          try {
            this.wait();
          } catch (InterruptedException e) {}
          v = this.value;
        }
      }
      return v;
    }

    /**
     * Write a value and awaken any processes waiting to read it.
     * @param value the value which everyone should read.
     */
    public void put (T value) {
      this.value = value;
      this.notifyAll();
    }
  }

}
