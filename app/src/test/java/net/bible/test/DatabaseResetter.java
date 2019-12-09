package net.bible.test;

import net.bible.service.db.DatabaseContainer;

import java.lang.reflect.Field;

/**
 * Reset db between tests @see https://github.com/robolectric/robolectric/issues/1890
 *
 * Created by mjden on 31/08/2017.
 */
public class DatabaseResetter {

	public static void resetDatabase() {
		resetSingleton(DatabaseContainer.class, "instance");
	}

	private static void resetSingleton(Class class_, String fieldName) {
		Field instance;
		try {
			instance = class_.getDeclaredField(fieldName);
			instance.setAccessible(true);
			instance.set(null, null);
		} catch (Exception e) {
			throw new RuntimeException();
		}
	}
}
