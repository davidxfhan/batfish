package org.batfish.symbolic.bdd;

import java.util.Arrays;
import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDException;
import net.sf.javabdd.BDDFactory;

public class BDDInteger {

  private BDDFactory _factory;

  private BDD[] _bitvec;

  /*
   * Create an integer, and initialize its values as "don't care"
   * This requires knowing the start index variables the bitvector
   * will use.
   */
  public static BDDInteger makeFromIndex(BDDFactory factory, int length, int start) {
    BDDInteger bdd = new BDDInteger(factory, length);
    for (int i = 0; i < length; i++) {
      bdd._bitvec[i] = bdd._factory.ithVar(start + i);
    }
    return bdd;
  }

  /*
   * Create an integer and initialize it to a concrete value
   */
  public static BDDInteger makeFromValue(BDDFactory factory, int length, long value) {
    BDDInteger bdd = new BDDInteger(factory, length);
    bdd.setValue(value);
    return bdd;
  }

  /*
   * Create an integer, but don't initialize its bit values
   */
  private BDDInteger(BDDFactory factory, int length) {
    _factory = factory;
    _bitvec = new BDD[length];
  }

  public BDDInteger(BDDInteger other) {
    _factory = other._factory;
    _bitvec = new BDD[other._bitvec.length];
    for (int i = 0; i < _bitvec.length; i++) {
      _bitvec[i] = other._bitvec[i].id();
    }
  }

  /*
   * Map an if-then-else over each bit in the bitvector
   */
  public BDDInteger ite(BDD b, BDDInteger other) {
    BDDInteger val = new BDDInteger(this);
    for (int i = 0; i < _bitvec.length; i++) {
      val._bitvec[i] = b.ite(_bitvec[i], other._bitvec[i]);
    }
    return val;
  }

  /*
   * Create a BDD representing the exact value
   */
  public BDD value(int val) {
    BDD bdd = _factory.one();
    for (int i = this._bitvec.length - 1; i >= 0; i--) {
      BDD b = this._bitvec[i];
      if ((val & 1) != 0) {
        bdd = bdd.and(b);
      } else {
        bdd = bdd.and(b.not());
      }
      val >>= 1;
    }
    return bdd;
  }

  /*
   * Less than or equal to on integers
   */
  public BDD leq(int val) {
    BDD[] eq = new BDD[_bitvec.length];
    BDD[] less = new BDD[_bitvec.length];
    for (int i = _bitvec.length - 1; i >= 0; i--) {
      if ((val & 1) != 0) {
        eq[i] = _bitvec[i];
        less[i] = _bitvec[i].not();
      } else {
        eq[i] = _bitvec[i].not();
        less[i] = _factory.zero();
      }
      val >>= 1;
    }
    BDD acc = _factory.one();
    for (int i = _bitvec.length - 1; i >= 0; i--) {
      acc = less[i].or(eq[i].and(acc));
    }
    return acc;
  }

  /*
   * Less than or equal to on integers
   */
  public BDD geq(int val) {
    BDD[] eq = new BDD[_bitvec.length];
    BDD[] greater = new BDD[_bitvec.length];
    for (int i = _bitvec.length - 1; i >= 0; i--) {
      if ((val & 1) != 0) {
        eq[i] = _bitvec[i];
        greater[i] = _factory.zero();
      } else {
        eq[i] = _bitvec[i].not();
        greater[i] = _bitvec[i];
      }
      val >>= 1;
    }
    BDD acc = _factory.one();
    for (int i = _bitvec.length - 1; i >= 0; i--) {
      acc = greater[i].or(eq[i].and(acc));
    }
    return acc;
  }

  /*
   * Set this BDD to have an exact value
   */
  public void setValue(long val) {
    for (int i = this._bitvec.length - 1; i >= 0; i--) {
      if ((val & 1) != 0) {
        this._bitvec[i] = _factory.one();
      } else {
        this._bitvec[i] = _factory.zero();
      }
      val >>= 1;
    }
  }

  /*
   * Set this BDD to be equal to another BDD
   */
  public void setValue(BDDInteger other) {
    for (int i = 0; i < this._bitvec.length; ++i) {
      this._bitvec[i] = other._bitvec[i].id();
    }
  }

  /*
   * Add two BDDs bitwise to create a new BDD
   */
  public BDDInteger add(BDDInteger var1) {
    if (this._bitvec.length != var1._bitvec.length) {
      throw new BDDException();
    } else {
      BDD var3 = _factory.zero();
      BDDInteger var4 = new BDDInteger(_factory, this._bitvec.length);
      for (int var5 = var4._bitvec.length - 1; var5 >= 0; --var5) {
        var4._bitvec[var5] = this._bitvec[var5].xor(var1._bitvec[var5]);
        var4._bitvec[var5] = var4._bitvec[var5].xor(var3.id());
        BDD var6 = this._bitvec[var5].or(var1._bitvec[var5]);
        var6 = var6.and(var3);
        BDD var7 = this._bitvec[var5].and(var1._bitvec[var5]);
        var7 = var7.or(var6);
        var3 = var7;
      }
      var3.free();
      return var4;
    }
  }

  /*
   * Subtract one BDD from another bitwise to create a new BDD
   */
  public BDDInteger sub(BDDInteger var1) {
    if (this._bitvec.length != var1._bitvec.length) {
      throw new BDDException();
    } else {
      BDD var3 = _factory.zero();
      BDDInteger var4 = new BDDInteger(_factory, this._bitvec.length);
      for (int var5 = var4._bitvec.length - 1; var5 >= 0; --var5) {
        var4._bitvec[var5] = this._bitvec[var5].xor(var1._bitvec[var5]);
        var4._bitvec[var5] = var4._bitvec[var5].xor(var3.id());
        BDD var6 = var1._bitvec[var5].or(var3);
        BDD var7 = this._bitvec[var5].apply(var6, BDDFactory.less);
        var6.free();
        var6 = this._bitvec[var5].and(var1._bitvec[var5]);
        var6 = var6.and(var3);
        var6 = var6.or(var7);
        var3 = var6;
      }
      var3.free();
      return var4;
    }
  }

  public BDD[] getBitvec() {
    return _bitvec;
  }

  public BDDFactory getFactory() {
    return _factory;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof BDDInteger)) {
      return false;
    }
    BDDInteger other = (BDDInteger) o;
    return Arrays.equals(_bitvec, other._bitvec);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(_bitvec);
  }
}
