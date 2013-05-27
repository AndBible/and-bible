package net.bible.service.common;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple implementation of BiMap without requiring Guava library
 * 
 * @see http://stackoverflow.com/questions/3430170/how-to-create-a-2-way-map-in-java
 */
public class TwoWayHashmap<K extends Object, V extends Object> {

	private Map<K, V> forward = new HashMap<K, V>();
	private Map<V, K> backward = new HashMap<V, K>();

	public synchronized void add(K key, V value) {
		forward.put(key, value);
		backward.put(value, key);
	}

	public synchronized void putForward(K key, V value) {
		forward.put(key, value);
		backward.put(value, key);
	}

	public synchronized void putBackward(V key, K value) {
		backward.put(key, value);
	}

	public synchronized V getForward(K key) {
		return forward.get(key);
	}

	public synchronized K getBackward(V key) {
		return backward.get(key);
	}
}