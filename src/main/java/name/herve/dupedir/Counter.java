package name.herve.dupedir;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang3.tuple.Pair;

public class Counter<T extends Comparable<T>> implements Iterable<T>, Serializable {
	private class MutableLong implements Serializable {
		private static final long serialVersionUID = -6501822308299601720L;

		private long val;

		public MutableLong(long val) {
			this.val = val;
		}

		public long get() {
			return val;
		}

		public void set(long val) {
			this.val = val;
		}

		@Override
		public String toString() {
			return Long.toString(val);
		}
	}

	private class SortedCount implements Iterable<Pair<T, Long>> {
		private List<Pair<T, Long>> data;

		public SortedCount() {
			this(false);
		}

		public SortedCount(boolean revert) {
			super();
			data = new ArrayList<>();
			for (Entry<T, MutableLong> e : cnt.entrySet()) {
				data.add(Pair.of(e.getKey(), e.getValue().val));
			}
			Collections.sort(data, new Comparator<Pair<T, Long>>() {
				@Override
				public int compare(Pair<T, Long> o1, Pair<T, Long> o2) {
					return revert ? Long.compare(o2.getValue(), o1.getValue()) : Long.compare(o1.getValue(), o2.getValue());
				}
			});
		}

		@Override
		public Iterator<Pair<T, Long>> iterator() {
			return data.iterator();
		}
	}

	private class SortedKey implements Iterable<Pair<T, Long>> {
		private List<Pair<T, Long>> data;

		public SortedKey() {
			this(false);
		}

		public SortedKey(boolean revert) {
			super();
			data = new ArrayList<>();
			for (Entry<T, MutableLong> e : cnt.entrySet()) {
				data.add(Pair.of(e.getKey(), e.getValue().val));
			}
			Collections.sort(data, new Comparator<Pair<T, Long>>() {
				@Override
				public int compare(Pair<T, Long> o1, Pair<T, Long> o2) {
					return revert ? o2.compareTo(o1) : o1.compareTo(o2);
				}
			});
		}

		@Override
		public Iterator<Pair<T, Long>> iterator() {
			return data.iterator();
		}
	}

	private static final long serialVersionUID = 7684295703301537701L;

	public static <T extends Comparable<T>> Counter<T> count(Collection<T> data) {
		Counter<T> cnt = new Counter<>();
		return cnt.add(data);
	}

	public static <T extends Comparable<T>> Counter<T> count(T[] data) {
		Counter<T> cnt = new Counter<>();
		return cnt.add(data);
	}

	private TreeMap<T, MutableLong> cnt;

	public Counter() {
		super();
		cnt = new TreeMap<>();
	}

	public Counter(T d) {
		this();
		add(d);
	}

	public synchronized Counter<T> add(Collection<T> data) {
		for (T d : data) {
			add(d);
		}
		return this;
	}

	public synchronized Counter<T> add(Counter<T> c) {
		for (Entry<T, MutableLong> e : c.cnt.entrySet()) {
			add(e.getKey(), e.getValue().get());
		}
		return this;
	}

	public synchronized Counter<T> add(T d) {
		MutableLong initValue = new MutableLong(1);
		MutableLong oldValue = cnt.put(d, initValue);

		if (oldValue != null) {
			initValue.set(oldValue.get() + 1);
		}

		return this;
	}

	public synchronized Counter<T> add(T d, long nb) {
		MutableLong initValue = new MutableLong(nb);
		MutableLong oldValue = cnt.put(d, initValue);

		if (oldValue != null) {
			initValue.set(oldValue.get() + nb);
		}

		return this;
	}

	public synchronized Counter<T> add(T[] data) {
		for (T d : data) {
			add(d);
		}
		return this;
	}

	public void clear() {
		cnt.clear();
	}

	public Counter<T> copy() {
		Counter<T> cp = new Counter<>();
		cp.add(this);
		return cp;
	}

	public void excludeMin(long nb) {
		List<T> res = new ArrayList<>();
		for (Entry<T, MutableLong> e : cnt.entrySet()) {
			if (e.getValue().get() < nb) {
				res.add(e.getKey());
			}
		}
		for (T t : res) {
			cnt.remove(t);
		}
	}

	public Set<T> filterMin(long nb) {
		Set<T> res = new HashSet<>();
		for (Entry<T, MutableLong> e : cnt.entrySet()) {
			if (e.getValue().get() >= nb) {
				res.add(e.getKey());
			}
		}
		return res;
	}

	public long getCount(T key) {
		MutableLong c = cnt.get(key);
		return c == null ? 0 : c.get();
	}

	public T getMax() {
		long mx = -1;
		T mxT = null;

		for (Entry<T, MutableLong> e : cnt.entrySet()) {
			if (e.getValue().get() > mx) {
				mx = e.getValue().get();
				mxT = e.getKey();
			}
		}

		return mxT;
	}

	public T getMaxExcluding(Set<T> ignore) {
		long mx = -1;
		T mxT = null;

		for (Entry<T, MutableLong> e : cnt.entrySet()) {
			if ((ignore == null) || !ignore.contains(e.getKey())) {
				if (e.getValue().get() > mx) {
					mx = e.getValue().get();
					mxT = e.getKey();
				}
			}
		}

		return mxT;
	}

	@SuppressWarnings("unchecked")
	public T getMaxExcluding(T... ignore) {
		Set<T> set = new HashSet<>();
		for (T i : ignore) {
			set.add(i);
		}

		return getMaxExcluding(set);
	}

	public long getSum() {
		long sum = 0;

		for (Entry<T, MutableLong> e : cnt.entrySet()) {
			sum += e.getValue().get();
		}

		return sum;
	}

	public synchronized Iterable<Pair<T, Long>> inverseSort() {
		return new SortedCount(true);
	}

	public synchronized Iterable<Pair<T, Long>> inverseSortKey() {
		return new SortedKey(true);
	}

	public boolean isEmpty() {
		return cnt.isEmpty();
	}

	@Override
	public Iterator<T> iterator() {
		return cnt.keySet().iterator();
	}

	public void keepTopK(int k) {
		keepTopKOrSet(k, null);
	}

	public void keepTopKOrSet(int k, Set<T> keys) {
		SortedCount sc = new SortedCount(true);
		int n = Math.min(k, cnt.size());

		Set<T> toRemove = new HashSet<>();
		for (int i = n; i < sc.data.size(); i++) {
			toRemove.add(sc.data.get(i).getKey());
		}

		if (keys != null) {
			toRemove.removeAll(keys);
		}

		for (T t : toRemove) {
			cnt.remove(t);
		}
	}

	public Set<T> keySet() {
		return cnt.keySet();
	}

	public Counter<T>.MutableLong remove(T key) {
		return cnt.remove(key);
	}

	public synchronized Counter<T> set(T key, long nb) {
		MutableLong initValue = new MutableLong(nb);
		cnt.put(key, initValue);

		return this;
	}

	public synchronized Counter<T> setMax(T key, long nb) {
		MutableLong c = cnt.get(key);
		if (c == null) {
			set(key, nb);
		} else {
			set(key, Math.max(nb, c.get()));
		}

		return this;
	}

	public synchronized Counter<T> setMin(T key, long nb) {
		MutableLong c = cnt.get(key);
		if (c == null) {
			set(key, nb);
		} else {
			set(key, Math.min(nb, c.get()));
		}

		return this;
	}

	public int size() {
		return cnt.size();
	}

	public synchronized Iterable<Pair<T, Long>> sort() {
		return new SortedCount();
	}

	public synchronized Iterable<Pair<T, Long>> sortKey() {
		return new SortedKey();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " " + cnt.toString();
	}
}
