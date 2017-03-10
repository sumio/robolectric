package org.robolectric;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.internal.Instrument;
import org.robolectric.internal.SandboxTestRunner;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.internal.bytecode.ShadowConstants;
import org.robolectric.internal.bytecode.SandboxConfig;
import org.robolectric.testing.AFinalClass;
import org.robolectric.testing.Pony;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

@RunWith(SandboxTestRunner.class)
public class ShadowingTest {

  @Test
  @SandboxConfig(shadows = {ShadowAccountManagerForTests.class})
  public void testStaticMethodsAreDelegated() throws Exception {
    Object arg = mock(Object.class);
    AccountManager.get(arg);
    assertThat(ShadowAccountManagerForTests.wasCalled).isTrue();
    assertThat(ShadowAccountManagerForTests.arg).isSameAs(arg);
  }

  @Implements(AccountManager.class)
  public static class ShadowAccountManagerForTests {
    public static boolean wasCalled = false;
    public static Object arg;

    public static AccountManager get(Object arg) {
      wasCalled = true;
      ShadowAccountManagerForTests.arg = arg;
      return mock(AccountManager.class);
    }
  }

  static class Context {
  }

  static class AccountManager {
    public static AccountManager get(Object arg) {
      return null;
    }
  }

  @Test
  @SandboxConfig(shadows = {ShadowClassWithProtectedMethod.class})
  public void testProtectedMethodsAreDelegated() throws Exception {
    ClassWithProtectedMethod overlay = new ClassWithProtectedMethod();
    assertEquals("shadow name", overlay.getName());
  }

  @Implements(ClassWithProtectedMethod.class)
  public static class ShadowClassWithProtectedMethod {
    @Implementation
    public String getName() {
      return "shadow name";
    }
  }

  @Instrument
  public static class ClassWithProtectedMethod {
    protected String getName() {
      return "protected name";
    }
  }

  @Test
  @SandboxConfig(shadows = {ShadowPaintForTests.class})
  public void testNativeMethodsAreDelegated() throws Exception {
    Paint paint = new Paint();
    paint.setColor(1234);

    Assertions.assertThat(paint.getColor()).isEqualTo(1234);
  }

  @Instrument
  static class Paint {
    public native void setColor(int color);
    public native int getColor();
  }

  @Implements(Paint.class)
  public static class ShadowPaintForTests {
    private int color;

    @Implementation
    public void setColor(int color) {
      this.color = color;
    }

    @Implementation
    public int getColor() {
      return color;
    }
  }

  @Implements(ClassWithNoDefaultConstructor.class)
  public static class ShadowForClassWithNoDefaultConstructor {
    public static boolean shadowDefaultConstructorCalled = false;
    public static boolean shadowDefaultConstructorImplementorCalled = false;

    public ShadowForClassWithNoDefaultConstructor() {
      this.shadowDefaultConstructorCalled = true;
    }

    public void __constructor__() {
      shadowDefaultConstructorImplementorCalled = true;
    }
  }

  @Instrument @SuppressWarnings({"UnusedDeclaration"})
  public static class ClassWithNoDefaultConstructor {
    ClassWithNoDefaultConstructor(String string) {
    }
  }

  @Test
  @SandboxConfig(shadows = {Pony.ShadowPony.class})
  public void directlyOn_shouldCallThroughToOriginalMethodBody() throws Exception {
    Pony pony = new Pony();

    assertEquals("Fake whinny! You're on my neck!", pony.ride("neck"));
    assertEquals("Whinny! You're on my neck!", Shadow.directlyOn(pony, Pony.class).ride("neck"));

    assertEquals("Fake whinny! You're on my haunches!", pony.ride("haunches"));
  }

  @Test
  @SandboxConfig(shadows = {Pony.ShadowPony.class})
  public void shouldCallRealForUnshadowedMethod() throws Exception {
    assertEquals("Off I saunter to the salon!", new Pony().saunter("the salon"));
  }

  static class TextView {
  }

  static class ColorStateList {
    public ColorStateList(int[][] ints, int[] ints1) {
    }
  }

  static class TypedArray {
  }

  @Implements(TextView.class)
  public static class TextViewWithDummyGetTextColorsMethod {
    public static ColorStateList getTextColors(Context context, TypedArray attrs) {
      return new ColorStateList(new int[0][0], new int[0]);
    }
  }

  @Test
  @SandboxConfig(shadows = ShadowOfClassWithSomeConstructors.class)
  public void shouldGenerateSeparatedConstructorBodies() throws Exception {
    ClassWithSomeConstructors o = new ClassWithSomeConstructors("my name");
    assertNull(o.name);

    Method realConstructor = o.getClass().getDeclaredMethod(ShadowConstants.CONSTRUCTOR_METHOD_NAME, String.class);
    realConstructor.setAccessible(true);
    realConstructor.invoke(o, "my name");
    assertEquals("my name", o.name);
  }

  @Instrument
  public static class ClassWithSomeConstructors {
    public String name;

    public ClassWithSomeConstructors(String name) {
      this.name = name;
    }
  }

  @Implements(ClassWithSomeConstructors.class)
  public static class ShadowOfClassWithSomeConstructors {
    @SuppressWarnings("UnusedDeclaration")
    public void __constructor__(String s) {
    }
  }

  @Test
  @SandboxConfig(shadows = {ShadowApiImplementedClass.class})
  public void withNonApiSubclassesWhichExtendApi_shouldStillBeInvoked() throws Exception {
    assertEquals("did foo", new NonApiSubclass().doSomething("foo"));
  }

  public static class NonApiSubclass extends ApiImplementedClass {
    public String doSomething(String value) {
      return "did " + value;
    }
  }

  @Instrument
  public static class ApiImplementedClass {
  }

  @Implements(ApiImplementedClass.class)
  public static class ShadowApiImplementedClass {
  }

  @Test
  public void shouldNotInstrumentClassIfNotAddedToConfig() {
    assertEquals(1, new NonInstrumentedClass().plus(0));
  }

  @Test
  @SandboxConfig(shadows = {ShadowNonInstrumentedClass.class})
  public void shouldInstrumentClassIfAddedToConfig() {
    assertEquals(2, new NonInstrumentedClass().plus(0));
  }

  public static class NonInstrumentedClass {
    public int plus(int x) {
      return x + 1;
    }
  }

  @Implements(NonInstrumentedClass.class)
  public static class ShadowNonInstrumentedClass {
    @Implementation
    public int plus(int x) {
      return x + 2;
    }
  }

  public void shouldNotInstrumentPackageIfNotAddedToConfig() throws Exception {
    Class<?> clazz = Class.forName(AFinalClass.class.getName());
    assertEquals(1, clazz.getModifiers() & Modifier.FINAL);
  }

  @Test
  @SandboxConfig(instrumentedPackages = {"org.robolectric.testing"})
  public void shouldInstrumentPackageIfAddedToConfig() throws Exception {
    Class<?> clazz = Class.forName(AFinalClass.class.getName());
    assertEquals(0, clazz.getModifiers() & Modifier.FINAL);
  }
}