package fj.data;

import fj.*;
import fj.function.TryEffect0;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;
import java.io.Reader;
import java.util.concurrent.atomic.AtomicBoolean;

import static fj.data.IOFunctions.*;
import static fj.data.Stream.cons;
import static fj.data.Stream.nil_;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class IOFunctionsTest {

  @Test
  public void bracket_happy_path() throws Exception {
    AtomicBoolean closed = new AtomicBoolean();
    Reader reader = new StringReader("Read OK") {
      @Override
      public void close() {
        super.close();
        closed.set(true);
      }
    };

    IO<String> bracketed = IOFunctions.bracket(
        () -> reader,
        IOFunctions.closeReader,
        r -> () -> new BufferedReader(r).readLine()
    );

    Assert.assertThat(bracketed.run(), is("Read OK"));
    Assert.assertThat(closed.get(), is(true));
  }

  @Test
  public void bracket_exception_path() throws Exception {
    AtomicBoolean closed = new AtomicBoolean();
    Reader reader = new StringReader("Read OK") {
      @Override
      public void close() {
        super.close();
        closed.set(true);
        throw new IllegalStateException("Should be suppressed");
      }
    };

    IO<String> bracketed = IOFunctions.bracket(
        () -> reader,
        IOFunctions.closeReader,
        r -> () -> {throw new IllegalArgumentException("OoO");}
    );

    try {
      bracketed.run();
      fail("Exception expected");
    } catch (IllegalArgumentException e) {
      Assert.assertThat(e.getMessage(), is("OoO"));
    }
    Assert.assertThat(closed.get(), is(true));
  }

  @Test
  public void testTraverseIO() throws IOException {
    String[] as = {"foo1", "bar2", "foobar3"};
    Stream<String> stream = Stream.arrayStream(as);
    ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(outContent));
    stream.traverseIO(IOFunctions::stdoutPrint).run();
    System.setOut(originalOut);
    assertThat(outContent.toString(), is("foobar3bar2foo1"));
  }

  @Test
  public void testSequenceWhile() throws IOException {
    BufferedReader r = new BufferedReader(new StringReader("foo1\nbar2\nfoobar3"));
    Stream<IO<String>> s1 = Stream.repeat(() -> r.readLine());
    IO<Stream<String>> io = sequenceWhile(s1, s -> !s.equals("foobar3"));
    assertThat(io.run(), is(cons("foo1", () -> cons("bar2", () -> Stream.nil()))));
  }

  @Test
  public void testForeach() throws IOException {
    Stream<IO<String>> s1 = Stream.repeat(() -> "foo1");
    IO<Stream<String>> io = sequence(s1.take(2));
    ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(outContent));
    runSafe(io).foreach(s -> runSafe(stdoutPrint(s)));
    System.setOut(originalOut);
    assertThat(outContent.toString(), is("foo1foo1"));
  }


  @Test
  public void testReplicateM() throws IOException {
    final IO<String> is = () -> new BufferedReader(new StringReader("foo")).readLine();
    assertThat(replicateM(is, 3).run(), is(List.list("foo", "foo", "foo")));
  }


  @Test
  public void testLift() throws IOException {
    final IO<String> readName = () -> new BufferedReader(new StringReader("foo")).readLine();
    final F<String, IO<String>> upperCaseAndPrint = F1Functions.<String, IO<String>, String>o(this::println).f(String::toUpperCase);
    final IO<String> readAndPrintUpperCasedName = IOFunctions.bind(readName, upperCaseAndPrint);
    assertThat(readAndPrintUpperCasedName.run(), is("FOO"));
  }

    @Test
    public void testFromTryEffect() throws IOException {
        final IO<Unit> io = fromTryEffect(new AlwaysSucceed0());
        assertThat(io.run(), is(Unit.unit()));
    }

    @Test
    public void testFromTryEffectFailure() {
        final IO<Unit> io = fromTryEffect(new AlwaysFail0("failure"));
        try {
            io.run();
            fail("Exception expected");
        } catch (IOException e) {
            assertThat(e, instanceOf(TryEffectException.class));
            assertThat(e.getMessage(), is("failure"));
        }
    }

    class AlwaysSucceed0 implements TryEffect0<TryEffectException> {
        @Override
        public void f() throws TryEffectException {
            // SUCCESS
        }
    }

    class AlwaysFail0 implements TryEffect0<TryEffectException> {

        private String message;

        public AlwaysFail0(String message) {
            this.message = message;
        }

        @Override
        public void f() throws TryEffectException {
            throw new TryEffectException(message);
        }
    }

    class TryEffectException extends IOException {
        public TryEffectException(String message) {
            super(message);
        }
    }

    private IO<String> println(final String s) {
    return () -> {
      return s;
    };
  }

}