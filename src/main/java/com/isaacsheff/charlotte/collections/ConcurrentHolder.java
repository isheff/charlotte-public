package com.isaacsheff.charlotte.collections;

/**
 * This is just a monitor.
 * Get just blocks until the value stored is non-null.
 * Put just fills in the value stored and then calls notifyAll().
 * @author Isaac Sheff
 */
public class ConcurrentHolder<T> {
  /** The value which waiting processes are waiting to read. */
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
    synchronized(this) {
      this.notifyAll();
    }
  }
}
