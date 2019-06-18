package se.de.hu_berlin.informatik.spectra.provider.tracecobertura.infrastructure;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

/**
 * An execution trace consists structurally of a list of executed nodes
 * and a list of tuples that mark repeated sequences in the trace.
 *
 * @param <T>
 * type of elements in the trace
 * @param <K>
 * type of a representation for storing elements in a map
 */
public abstract class CompressedTraceBase<T, K> implements Serializable, Iterable<T> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5903218865249529299L;
	
	private static final int MAX_ITERATION_COUNT = 10;
	
	private int originalSize;
	private BufferedArrayQueue<T> compressedTrace;
	private BufferedMap<int[]> repetitionMarkers;
	
	private CompressedTraceBase<T,K> child;
	
	/**
	 * Adds the given queue's contents to the trace. 
	 * ATTENTION: the queue's contents will be removed in the process! 
	 * @param trace
	 * a queue that should be compressed
	 * @param log
	 * whether to log some status information
	 */
	public CompressedTraceBase(BufferedArrayQueue<T> trace, boolean log) {
		this(trace, log, 0);
	}
	
	private CompressedTraceBase(BufferedArrayQueue<T> trace, boolean log, int iteration) {
		this.originalSize = trace.size();
		// inherit whether buffered trace files should be deleted on exit
		BufferedArrayQueue<T> traceWithoutRepetitions = extractRepetitions(trace, log, trace.isDeleteOnExit());
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
	
	public CompressedTraceBase(BufferedArrayQueue<T> traceOfNodeIDs, CompressedTraceBase<?,?> otherCompressedTrace) {
		if (otherCompressedTrace.getChild() == null) {
			this.compressedTrace = traceOfNodeIDs;
		} else {
			this.repetitionMarkers = otherCompressedTrace.getRepetitionMarkers();
			this.child = newChildInstance(traceOfNodeIDs, otherCompressedTrace.getChild());
			this.originalSize = computeFullTraceLength();
		}
	}
	
	public CompressedTraceBase(BufferedArrayQueue<T> compressedTrace, BufferedArrayQueue<int[]> repetitionMarkers, int index) {
		if (repetitionMarkers == null || index >= repetitionMarkers.size()) {
			this.compressedTrace = compressedTrace;
		} else {
			this.repetitionMarkers = constructFromArray(repetitionMarkers.get(index), 
					compressedTrace.getOutputDir(), 
					compressedTrace.getFilePrefix() + "-map-" + index, compressedTrace.arrayLength * 3,
					compressedTrace.isDeleteOnExit());
			this.child = newChildInstance(compressedTrace, repetitionMarkers, ++index);
			this.originalSize = computeFullTraceLength();
		}
	}
	
	private BufferedMap<int[]> constructFromArray(int[] repetitionMarkers, File outputDir, String filePreix, int subMapSize, boolean deleteOnExit) {
		BufferedMap<int[]> map = new BufferedMap<>(outputDir, filePreix, subMapSize, deleteOnExit);
		for (int i = 0; i < repetitionMarkers.length; i += 3) {
			map.put(repetitionMarkers[i], new int[] {repetitionMarkers[i+1], repetitionMarkers[i+2]});
		}
		return map;
	}

	public abstract CompressedTraceBase<T,K> newChildInstance(BufferedArrayQueue<T> trace, CompressedTraceBase<?,?> otherCompressedTrace);
	
	public abstract CompressedTraceBase<T,K> newChildInstance(BufferedArrayQueue<T> compressedTrace, BufferedArrayQueue<int[]> repetitionMarkers, int index);
	
	public abstract CompressedTraceBase<T,K> newChildInstance(BufferedArrayQueue<T> trace, boolean log, int iteration);
	
	public int getMaxStoredValue() {
		throw new UnsupportedOperationException("not implemented!");
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
//	BufferedArrayQueue<T> traceWithoutRepetitions;
//	BufferedMap<int[]> traceRepetitions;
//	BufferedArrayQueue<T> bufferTrace;
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

	private BufferedArrayQueue<T> extractRepetitions(BufferedArrayQueue<T> trace, boolean log, boolean deleteOnExit) {
		String filePrefix = "cpr_trace_" + UUID.randomUUID().toString();
		BufferedArrayQueue<T> traceWithoutRepetitions = 
				new BufferedArrayQueue<>(trace.getOutputDir(), filePrefix, trace.arrayLength, deleteOnExit);
		BufferedMap<int[]> traceRepetitions = new BufferedMap<>(trace.getOutputDir(), "cpr_trace_rpt_" + UUID.randomUUID().toString(), trace.arrayLength * 3, deleteOnExit);
		
		// mapping from elements to their most recent positions in the result list
		Map<K,Integer> elementToPositionMap = new HashMap<>();
		while (!trace.isEmpty()) {
			T element = trace.remove();
			K repr = getRepresentation(element);

			// check for repetition of the current element
			Integer position = elementToPositionMap.get(repr);
			if (position == null) {
				// no repetition: remember node containing the element
				elementToPositionMap.put(repr, traceWithoutRepetitions.size());
				// build up the result trace on the fly
				traceWithoutRepetitions.add(element);
			} else {
				// element was repeated
				// check if the sequence of elements between the last position of the element
				// and this position is the same as the following sequence(s) in the input trace
				int repetitionCounter = 0;
				int lengthToRemove = 0;
				Iterator<T> inputTraceIterator = trace.iterator();
				Iterator<T> resultTraceIterator = traceWithoutRepetitions.iterator(position);
				resultTraceIterator.next();
				// count the number of elements that need to be removed later with count variable;
				// variable count can start at 0 here, since we already removed the very first element
				for (int count = 0; ; ++count) {
					if (!resultTraceIterator.hasNext()) {
						// at the end of the sequence
						++repetitionCounter;
						// start over
						resultTraceIterator = traceWithoutRepetitions.iterator(position);
						// later remove the processed nodes that have been repeated
						lengthToRemove += count;
						count = 0;
					}
					
					if (!inputTraceIterator.hasNext()) {
						// no further remaining sequence
						break;
					}
					
					// check if elements are equal
					T first = inputTraceIterator.next();
					T second = resultTraceIterator.next();
					if (!isEqual(first, second)) {
						break;
					}
					
					// continue with the next node of the remaining sequence
				}
				// remove repeated elements
				trace.clear(lengthToRemove);
				

				// are there any repetitions?
				if (repetitionCounter > 0) {
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
					elementToPositionMap.put(repr, traceWithoutRepetitions.size());
					// build up the result trace on the fly
					traceWithoutRepetitions.add(element);
				}
			}
		}

		if (!traceRepetitions.isEmpty()) {
			this.repetitionMarkers = traceRepetitions;
		}
		return traceWithoutRepetitions;
	}

	public abstract boolean isEqual(T first, T second);

	public abstract K getRepresentation(T element);

	public BufferedArrayQueue<T> getCompressedTrace() {
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

	public CompressedTraceBase<T,K> getChild() {
		return child;
	}

	@Override
	public TraceIterator<T> iterator() {
		return new TraceIterator<>(this);
	}
	
	public Set<K> computeStartingElements() {
		Set<K> set = new HashSet<>();
		addStartingElementsToSet(set);
		return set;
	}
	
	public void addStartingElementsToSet(Set<K> set) {
		TraceIterator<T> iterator = iterator();
		boolean lastElementWasSequenceEnd = false;
		if (iterator.hasNext()) {
			lastElementWasSequenceEnd = iterator.isEndOfRepetition();
			set.add(getRepresentation(iterator.next()));
		}
		while (iterator.hasNext()) {
			if (lastElementWasSequenceEnd || iterator.isStartOfRepetition()) {
				lastElementWasSequenceEnd = iterator.isEndOfRepetition();
				set.add(getRepresentation(iterator.next()));
			} else {
				lastElementWasSequenceEnd = iterator.isEndOfRepetition();
				iterator.next();
			}
		}
	}

	public void clear() {
		if (child != null) {
			if (repetitionMarkers != null) {
				repetitionMarkers.clear();
			}
			child.clear();
		} else {
			compressedTrace.clear();
		}
	}
	
}