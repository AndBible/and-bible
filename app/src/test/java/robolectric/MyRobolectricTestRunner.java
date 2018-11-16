package robolectric;

import org.junit.runners.model.InitializationError;
import org.robolectric.RobolectricTestRunner;

/**
 * DEPRECATED. We could use directly RobolectircTestRunner instead
 */
public class MyRobolectricTestRunner extends RobolectricTestRunner {
	public MyRobolectricTestRunner(Class<?> klass) throws InitializationError {
		super(klass);
	}
}