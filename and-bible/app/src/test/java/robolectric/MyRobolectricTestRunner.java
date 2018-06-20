package robolectric;

import org.junit.runners.model.InitializationError;
import org.robolectric.RobolectricTestRunner;

/**
 * DEPRECATED. RobolectircTestRunner can be used as is.
 */
public class MyRobolectricTestRunner extends RobolectricTestRunner {
    public MyRobolectricTestRunner(Class<?> klass) throws InitializationError {
		super(klass);
    }
}