package net.bible.test;

import net.bible.service.db.CommonDatabaseHelper;

import java.lang.reflect.Field;

/**
 * Reset db between tests @see https://github.com/robolectric/robolectric/issues/1890
 *
 * Created by mjden on 31/08/2017.
 */
public class DatabaseResetter {

	public static void resetDatabase() {
		resetSingleton(CommonDatabaseHelper.class, "sSingleton");
	}

	private static void resetSingleton(Class clazz, String fieldName) {
		Field instance;
		try {
			instance = clazz.getDeclaredField(fieldName);
			instance.setAccessible(true);
			instance.set(null, null);
		} catch (Exception e) {
			throw new RuntimeException();
		}
	}
}