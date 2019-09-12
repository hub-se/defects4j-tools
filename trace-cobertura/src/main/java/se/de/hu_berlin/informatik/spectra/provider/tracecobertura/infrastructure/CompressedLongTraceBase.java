package se.de.hu_berlin.informatik.spectra.provider.tracecobertura.infrastructure;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import se.de.hu_berlin.informatik.spectra.provider.tracecobertura.infrastructure.BufferedLongArrayQueue.MyBufferedLongIterator;

import java.util.Set;
import java.util.UUID;

/**
 * An execution trace consists structurally of a list of executed nodes
 * and a list of tuples that mark repeated sequences in the trace.
 */
public abstract class CompressedLongTraceBase implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3678143596875267880L;

	private static final int MAX_ITERATION_COUNT = 10;
	
	private int originalSize;
	private BufferedLongArrayQueue compressedTrace;
	private BufferedMap<int[]> repetitionMarkers;
	
	private CompressedLongTraceBase child;

	private transient boolean markedForDeletion;
	
	/**
	 * Adds the given queue's contents to the trace. 
	 * ATTENTION: the queue's contents will be removed in the process! 
	 * @param trace
	 * a queue that should be compressed
	 * @param log
	 * whether to log some status information
	 */
	public CompressedLongTraceBase(BufferedLongArrayQueue trace, boolean log) {
		this(trace, log, 0);
	}
	
	private CompressedLongTraceBase(BufferedLongArrayQueue trace, boolean log, int iteration) {
		this.originalSize = trace.size();
		// inherit whether buffered trace files should be deleted on exit
		BufferedLongArrayQueue traceWithoutRepetitions = extractRepetitions(trace, log, trace.isDeleteOnExit());
		trace = null;
		// did something change?
		if (originalSize == traceWithoutRepetitions.size() || ++iteration >= MAX_ITERATION_COUNT) {
			if (log) {
				System.out.println("=> " + originalSize);
			}
			// no... then just store the compressed trace
			this.compressedTrace = traceWithoutRepetitions;
		} else {
			if (log) {
				System.out.println(originalSize + " -> " + traceWithoutRepetitions.size());
			}
			// yes... then try again recursively
			this.child = newChildInstance(traceWithoutRepetitions, log, iteration);
		}
	}
	
	public CompressedLongTraceBase(BufferedLongArrayQueue traceOfNodeIDs, CompressedLongTraceBase otherCompressedTrace) {
		if (otherCompressedTrace.getChild() == null) {
			this.compressedTrace = traceOfNodeIDs;
		} else {
			this.repetitionMarkers = otherCompressedTrace.getRepetitionMarkers();
			this.child = newChildInstance(traceOfNodeIDs, otherCompressedTrace.getChild());
			this.originalSize = computeFullTraceLength();
		}
	}
	
	public CompressedLongTraceBase(BufferedLongArrayQueue compressedTrace, BufferedArrayQueue<int[]> repetitionMarkers, int index) {
		if (repetitionMarkers == null || index >= repetitionMarkers.size()) {
			this.compressedTrace = compressedTrace;
		} else {
			this.repetitionMarkers = constructFromArray(repetitionMarkers.get(index), 
					compressedTrace.getOutputDir(), 
					compressedTrace.getFilePrefix() + "-map-" + index, compressedTrace.arrayLength,
					compressedTrace.isDeleteOnExit());
			this.child = newChildInstance(compressedTrace, repetitionMarkers, ++index);
			this.originalSize = computeFullTraceLength();
		}
	}
	
	private BufferedMap<int[]> constructFromArray(int[] repetitionMarkers, File outputDir, String filePrefix, int subMapSize, boolean deleteOnExit) {
		BufferedMap<int[]> map = new RepetitionMarkerBufferedMap(outputDir, filePrefix, subMapSize, deleteOnExit);
		for (int i = 0; i < repetitionMarkers.length; i += 3) {
			map.put(repetitionMarkers[i], new int[] {repetitionMarkers[i+1], repetitionMarkers[i+2]});
		}
		return map;
	}

	public abstract CompressedLongTraceBase newChildInstance(BufferedLongArrayQueue trace, CompressedLongTraceBase otherCompressedTrace);
	
	public abstract CompressedLongTraceBase newChildInstance(BufferedLongArrayQueue compressedTrace, BufferedArrayQueue<int[]> repetitionMarkers, int index);
	
	public abstract CompressedLongTraceBase newChildInstance(BufferedLongArrayQueue trace, boolean log, int iteration);
	
	public long getMaxStoredValue() {
		if (getChild() == null) {
			long max = 0;
			ReplaceableCloneableLongIterator iterator = getCompressedTrace().iterator();
			while (iterator.hasNext()) {
				max = Math.max(iterator.next(), max);
			}
			return max;
		} else {
			long max = getChild().getMaxStoredValue();
			Iterator<Entry<Integer, int[]>> entrySetIterator = getRepetitionMarkers().entrySetIterator();
			while (entrySetIterator.hasNext()) {
				Entry<Integer, int[]> entry = entrySetIterator.next();
				max = Math.max(entry.getKey(), max);
				max = Math.max(entry.getValue()[0], max);
				max = Math.max(entry.getValue()[1], max);
			}
			max = Math.max(getRepetitionMarkers().size(), max);
			return max;
		}
	}
	
	private int computeFullTraceLength() {
		if (child == null) {
			return compressedTrace.size();
		}
		
		int length = child.computeFullTraceLength();
		
		for (Iterator<Entry<Integer, int[]>> iterator = repetitionMarkers.entrySetIterator(); iterator.hasNext();) {
			Entry<Integer, int[]> i = iterator.next();
			// [length, repetitionCount]
			length += (i.getValue()[0] * (i.getValue()[1]-1));
		}
		return length;
	}
	
	public int size() {
		return originalSize;
	}
	
//	// mapping from elements to their most recent positions in the result list
//	Map<K,Integer> elementToPositionMap = new HashMap<>();
//	BufferedIntArrayQueue traceWithoutRepetitions;
//	BufferedMap<int[]> traceRepetitions;
//	BufferedIntArrayQueue bufferTrace;
//	
//	boolean possibleRepetition = false;
//	Integer position;
//	int elementCount = 0;
//	int repetitionCounter = 0;
//	Iterator<T> resultTraceIterator;
//	
//	public void add(T element) {
//		if (possibleRepetition) {
//
//				if (!resultTraceIterator.hasNext()) {
//					// at the end of the sequence
//					++repetitionCounter;
//					// start over
//					resultTraceIterator = traceWithoutRepetitions.iterator(position);
//					// remove the processed nodes that have been repeated
//					bufferTrace.clear();
//				}
//
//
//				// check if elements are equal
//				if (!isEqual(element, resultTraceIterator.next())) {
//					// end of equal sequence
//					// are there any repetitions?
//					if (repetitionCounter > 0) {
//						// compute the length of one repetition
//						int length = (lengthToRemove+1)/repetitionCounter;
//
//						// add a triplet to the list
//						traceRepetitions.put(traceWithoutRepetitions.size() - length, new int[] { length, repetitionCounter + 1 });
//						//				if (log) {
//						//					System.out.println("idx: " + (traceWithoutRepetitions.size() - length) + ", len: " + length + ", rpt: " + (repetitionCounter+1));
//						//				}
//
//						// reset repetition recognition
//						elementToPositionMap.clear();
//					} else {
//						// no repetition found...
//						// no repetition: remember only the last node containing the element (update)
//						elementToPositionMap.put(repr, traceWithoutRepetitions.size());
//						// build up the result trace on the fly
//						traceWithoutRepetitions.add(element);
//					}
//				}
//		} else {
//			K repr = getRepresentation(element);
//
//			// check for repetition of the current element
//			position = elementToPositionMap.get(repr);
//			if (position == null) {
//				// no repetition: remember node containing the element
//				elementToPositionMap.put(repr, traceWithoutRepetitions.size());
//				// build up the result trace on the fly
//				traceWithoutRepetitions.add(element);
//			} else {
//				possibleRepetition = true;
//				resultTraceIterator = traceWithoutRepetitions.iterator(position);
//				resultTraceIterator.next();
//				bufferTrace.add(element);
//			}
//		}
//	}

	private BufferedLongArrayQueue extractRepetitions(BufferedLongArrayQueue trace, boolean log, boolean deleteOnExit) {
		String filePrefix = "cpr_trace_" + UUID.randomUUID().toString();
		BufferedLongArrayQueue traceWithoutRepetitions = 
				new BufferedLongArrayQueue(trace.getOutputDir(), filePrefix, 
						trace.getNodeSize(), deleteOnExit);
		BufferedMap<int[]> traceRepetitions = new RepetitionMarkerBufferedMap(trace.getOutputDir(), 
				"cpr_trace_rpt_" + UUID.randomUUID().toString(), trace.arrayLength, deleteOnExit);
		MyBufferedLongIterator resultTraceIterator = traceWithoutRepetitions.iterator();
		MyBufferedLongIterator inputTraceIterator = trace.iterator();
		
		// mapping from elements to their most recent positions in the result list
		Map<Long,Integer> elementToPositionMap = new HashMap<>();
		while (!trace.isEmpty()) {
			long element = trace.remove();

			// check for repetition of the current element
			Integer position = elementToPositionMap.get(element);
			if (position == null) {
				// no repetition: remember node containing the element
				elementToPositionMap.put(element, traceWithoutRepetitions.size());
				// build up the result trace on the fly
				traceWithoutRepetitions.add(element);
			} else {
				// element was repeated
				// check if the sequence of elements between the last position of the element
				// and this position is the same as the following sequence(s) in the input trace
				int repetitionCounter = 0;
				int lengthToRemove = 0;
				// avoid instantiating new iterators
				inputTraceIterator.setToPosition(0);
				resultTraceIterator.setToPosition(position+1);
//				resultTraceIterator.next();
				// count the number of elements that need to be removed later with count variable;
				// variable count can start at 0 here, since we already removed the very first element
				for (int count = 0; ; ++count) {
					if (!resultTraceIterator.hasNext()) {
						// at the end of the sequence
						++repetitionCounter;
						// start over
						resultTraceIterator.setToPosition(position);
						// later remove the processed nodes that have been repeated
						lengthToRemove += count;
						count = 0;
					}
					
					if (!inputTraceIterator.hasNext()) {
						// no further remaining sequence
						break;
					}
					
					// check if elements are equal
					if (inputTraceIterator.next() != resultTraceIterator.next()) {
						break;
					}
					
					// continue with the next node of the remaining sequence
				}

				// are there any repetitions?
				if (repetitionCounter > 0) {
					// remove repeated elements
					trace.clear(lengthToRemove);
					// compute the length of one repetition
					int length = (lengthToRemove+1)/repetitionCounter;
					
					// add a triplet to the list
					traceRepetitions.put(traceWithoutRepetitions.size() - length, new int[] { length, repetitionCounter + 1 });
//					if (log) {
//						System.out.println("idx: " + (traceWithoutRepetitions.size() - length) + ", len: " + length + ", rpt: " + (repetitionCounter+1));
//					}
					
					// reset repetition recognition
					elementToPositionMap.clear();
				} else {
					// no repetition found...
					// no repetition: remember only the last node containing the element (update)
					elementToPositionMap.put(element, traceWithoutRepetitions.size());
					// build up the result trace on the fly
					traceWithoutRepetitions.add(element);
				}
			}
		}

		if (!traceRepetitions.isEmpty()) {
			traceRepetitions.sleep();
			this.repetitionMarkers = traceRepetitions;
		}
		return traceWithoutRepetitions;
	}

	public BufferedLongArrayQueue getCompressedTrace() {
		if (child != null) {
			return child.getCompressedTrace();
		} else {
			return compressedTrace;
		}
	}

	public BufferedMap<int[]> getRepetitionMarkers() {
		return repetitionMarkers;
	}
	
	
	
//	public T[] reconstructFullTrace() {
//		return reconstructTrace();
//	}
//
//	protected T[] reconstructTrace() {
//		if (child == null) {
//			return this.compressedTrace;
//		}
//		
//		// got a child object? then reconstruct the trace...
//		T[] compressedTrace = child.reconstructTrace();
//
//		T[] result = newArrayOfSize(originalSize);
//		int startPos = 0;
//		int currentIndex = 0;
//		Set<Integer> keySet = repetitionMarkers.keySet();
//		int k = 0;
//		int[] keyArray = new int[keySet.size()];
//		for (Iterator<Integer> iterator = keySet.iterator(); iterator.hasNext();) {
//			keyArray[k++] = iterator.next();
//		}
//		Arrays.sort(keyArray);
//		for (int j = 0; j < keyArray.length; ++j) {
//			int[] repetitionMarker = repetitionMarkers.get(keyArray[j]);
//			// key: rangeStart, value: [length, repetitionCount]
//			int unprocessedLength = keyArray[j] - startPos;
//			if (unprocessedLength > 0) {
//				// the previous sequence has not been repeated
//				System.arraycopy(compressedTrace, startPos, result, currentIndex, unprocessedLength);
//				// move the index for the result array
//				currentIndex += unprocessedLength;
//			}
//
//			// add the repeated sequences
//			for (int i = 0; i < repetitionMarker[1]; ++i) {
//				System.arraycopy(compressedTrace, keyArray[j], result, currentIndex, repetitionMarker[0]);
//				currentIndex += repetitionMarker[0];
//			}
//
//			// move the start position in the source trace array
//			startPos = keyArray[j] + repetitionMarker[0];
//		}
//
//		if (startPos < compressedTrace.length) {
//			// the remaining sequence has not been repeated
//			System.arraycopy(compressedTrace, startPos, result, currentIndex, compressedTrace.length - startPos);
//		}
//		compressedTrace = null;
//
//		return result;
//	}
//
//	public abstract T[] newArrayOfSize(int size);

	public CompressedLongTraceBase getChild() {
		return child;
	}

	public LongTraceIterator iterator() {
		return new LongTraceIterator(this);
	}
	
	public Set<Long> computeStartingElements() {
		Set<Long> set = new HashSet<>();
		addStartingElementsToSet(set);
		return set;
	}
	
	public void addStartingElementsToSet(Set<Long> set) {
		LongTraceIterator iterator = iterator();
		boolean lastElementWasSequenceEnd = false;
		if (iterator.hasNext()) {
			lastElementWasSequenceEnd = iterator.isEndOfRepetition();
			set.add(iterator.next());
		}
		while (iterator.hasNext()) {
			if (lastElementWasSequenceEnd || iterator.isStartOfRepetition()) {
				lastElementWasSequenceEnd = iterator.isEndOfRepetition();
				set.add(iterator.next());
			} else {
				lastElementWasSequenceEnd = iterator.isEndOfRepetition();
				iterator.next();
			}
		}
	}

	public void clear() {
		if (repetitionMarkers != null) {
			repetitionMarkers.clear();
		}
		if (child != null) {
			child.clear();
		} else {
			compressedTrace.clear();
		}
	}

	public boolean isEmpty() {
		return originalSize == 0;
	}

	public long getFirstElement() {
		return getCompressedTrace().element();
	}

	public long getLastElement() {
		return getCompressedTrace().lastElement();
	}

	public void sleep() {
		getCompressedTrace().sleep();
	}
	
	public void lock() {
		getCompressedTrace().lock();
	}
	
	public void unlock() {
		getCompressedTrace().unlock();
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder(originalSize + " ==> ");
		LongTraceIterator eTraceIterator = iterator();
		while (eTraceIterator.hasNext()) {
			builder.append(eTraceIterator.next() + ", ");
		}
		return builder.toString();
	}

	public void markForDeletion() {
		this.markedForDeletion = true;
	}
	
	public void deleteIfMarked() {
		if (markedForDeletion) {
			this.unlock();
			this.clear();
		}
	}
	
	public void deleteOnExit() {
		if (repetitionMarkers != null) {
			repetitionMarkers.deleteOnExit();
		}
		if (child != null) {
			child.deleteOnExit();
		} else {
			compressedTrace.deleteOnExit();
		}
	}
}