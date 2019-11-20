/*
 * This file is part of the "STARDUST" project. (c) Fabian Keller
 * <hello@fabian-keller.de> For the full copyright and license information,
 * please view the LICENSE file that was distributed with this source code.
 */

package se.de.hu_berlin.informatik.spectra.provider.loader.tracecobertura.report;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import se.de.hu_berlin.informatik.spectra.core.ISpectra;
import se.de.hu_berlin.informatik.spectra.core.ITrace;
import se.de.hu_berlin.informatik.spectra.core.SourceCodeBlock;
import se.de.hu_berlin.informatik.spectra.core.Node.NodeType;
import se.de.hu_berlin.informatik.spectra.core.traces.RawIntTraceCollector;
import se.de.hu_berlin.informatik.spectra.core.traces.SimpleIntIndexerCompressed;
import se.de.hu_berlin.informatik.spectra.provider.loader.AbstractCoverageDataLoader;
import se.de.hu_berlin.informatik.spectra.provider.tracecobertura.coveragedata.ClassData;
import se.de.hu_berlin.informatik.spectra.provider.tracecobertura.coveragedata.ExecutionTraceCollector;
import se.de.hu_berlin.informatik.spectra.provider.tracecobertura.coveragedata.JumpData;
import se.de.hu_berlin.informatik.spectra.provider.tracecobertura.coveragedata.LineData;
import se.de.hu_berlin.informatik.spectra.provider.tracecobertura.coveragedata.PackageData;
import se.de.hu_berlin.informatik.spectra.provider.tracecobertura.coveragedata.SourceFileData;
import se.de.hu_berlin.informatik.spectra.provider.tracecobertura.coveragedata.SwitchData;
import se.de.hu_berlin.informatik.spectra.provider.tracecobertura.data.CoverageData;
import se.de.hu_berlin.informatik.spectra.provider.tracecobertura.infrastructure.CoberturaStatementEncoding;
import se.de.hu_berlin.informatik.spectra.provider.tracecobertura.infrastructure.comptrace.integer.EfficientCompressedIntegerTrace;
import se.de.hu_berlin.informatik.spectra.provider.tracecobertura.infrastructure.comptrace.integer.IntTraceIterator;
import se.de.hu_berlin.informatik.spectra.provider.tracecobertura.coveragedata.ProjectData;
import se.de.hu_berlin.informatik.spectra.provider.tracecobertura.report.TraceCoberturaReportWrapper;
import se.de.hu_berlin.informatik.utils.miscellaneous.Log;
import se.de.hu_berlin.informatik.utils.tracking.ProgressTracker;

public abstract class TraceCoberturaReportLoader<T, K extends ITrace<T>>
		extends AbstractCoverageDataLoader<T, K, TraceCoberturaReportWrapper> {

    private final RawIntTraceCollector traceCollector;
	private ProjectData projectData;
	
	private Map<Long, Integer> idToSubtraceIdMap = new HashMap<>();
	private Map<Integer, EfficientCompressedIntegerTrace> existingSubTraces = new HashMap<>();

	public TraceCoberturaReportLoader(Path tempOutputDir) {
		traceCollector = new RawIntTraceCollector(tempOutputDir);
	}
	
	private int traceCount = 0;
	private int currentId = 0;
	
	@Override
	public boolean loadSingleCoverageData(ISpectra<T, K> lineSpectra, final TraceCoberturaReportWrapper reportWrapper,
			final boolean fullSpectra) {
		if (reportWrapper == null || reportWrapper.getReport() == null) {
			return false;
		}

		
		K trace = null;

		ProjectData projectData = reportWrapper.getReport().getProjectData();
		if (projectData == null) {
			return false;
		} else if (this.projectData == null) {
			this.projectData = projectData;
		}

		String testId;
		if (reportWrapper.getIdentifier() == null) {
			testId = String.valueOf(++traceCount);
		} else {
			++traceCount;
			testId = reportWrapper.getIdentifier();
		}
		
		try {
		trace = lineSpectra.addTrace(testId, traceCount, reportWrapper.isSuccessful());
		
		if (projectData.isReset()) {
			Log.warn(this, "Test '%s' produced no coverage.", testId);
			return true;
		}
		
		boolean coveredLines = false;
		
//		Log.out(true, this, "Processing Test: " + reportWrapper.getIdentifier());
		
		// loop over all packages
		for (CoverageData coverageData2 : projectData.getPackages()) {
			PackageData packageData = (PackageData) coverageData2;
			final String packageName = packageData.getName();

			onNewPackage(packageName, trace);

			// loop over all classes of the package
			for (SourceFileData sourceFileData : packageData.getSourceFiles()) {
				for (CoverageData coverageData1 : sourceFileData.getClasses()) {
					ClassData classData = (ClassData) coverageData1;
					// TODO: use actual class name!?
					final String actualClassName = classData.getName();
					final String sourceFilePath = classData.getSourceFileName();

					onNewClass(packageName, sourceFilePath, trace);

					// loop over all methods of the class
					// SortedSet<String> sortedMethods = new TreeSet<>();
					// sortedMethods.addAll(classData.getMethodNamesAndDescriptors());
					for (String methodNameAndSig : classData.getMethodNamesAndDescriptors()) {
						// String name = methodNameAndSig.substring(0,
						// methodNameAndSig.indexOf('('));
						// String signature =
						// methodNameAndSig.substring(methodNameAndSig.indexOf('('));

						final String methodIdentifier = String.format("%s:%s", actualClassName, methodNameAndSig);

						onNewMethod(packageName, sourceFilePath, methodIdentifier, trace);

						// loop over all lines of the method
						// SortedSet<CoverageData> sortedLines = new
						// TreeSet<>();
						// sortedLines.addAll(classData.getLines(methodNameAndSig));
						for (CoverageData coverageData : classData.getLines(methodNameAndSig)) {
							LineData lineData = (LineData) coverageData;

							// set node involvement
							T lineIdentifier = getIdentifier(
									packageName, sourceFilePath, methodNameAndSig, lineData.getLineNumber(), NodeType.NORMAL);

							long hits = lineData.getHits();
							if (hits > 0) {
								coveredLines = true;
							}
							onNewLine(
									packageName, sourceFilePath, methodIdentifier, lineIdentifier, lineSpectra, trace,
									fullSpectra, hits);
							
							if (lineData.hasBranch()) {
								int conditionSize = lineData.getConditionSize();
								for (int index = 0; index < conditionSize; ++index) {
									Object branchData = lineData.getConditionData(index);
									if (branchData == null) {
										continue;
									} else if (branchData instanceof JumpData) {
										JumpData jumpData = (JumpData) branchData;
										
										// add nodes for false branches (the counters point at the "wrong" boolean)
										T lineIdentifier2 = getIdentifier(
												packageName, sourceFilePath, methodNameAndSig, lineData.getLineNumber(), NodeType.FALSE_BRANCH);
//										Log.out(this, "%s, F: %d", lineIdentifier.toString(), jumpData.getTrueHits());
										onNewLine(
												packageName, sourceFilePath, methodIdentifier, lineIdentifier2, lineSpectra, trace,
												fullSpectra, jumpData.getTrueHits());
										
										// add nodes for true branches (the counters point at the "wrong" boolean)
										T lineIdentifier3 = getIdentifier(
												packageName, sourceFilePath, methodNameAndSig, lineData.getLineNumber(), NodeType.TRUE_BRANCH);
//										Log.out(this, "%s, T: %d", lineIdentifier.toString(), jumpData.getFalseHits());
										onNewLine(
												packageName, sourceFilePath, methodIdentifier, lineIdentifier3, lineSpectra, trace,
												fullSpectra, jumpData.getFalseHits());
									} else if (branchData instanceof SwitchData) {
										SwitchData switchData = (SwitchData) branchData;
										
										// add nodes for default branch of switch statements
										T lineIdentifier4 = getIdentifier(
												packageName, sourceFilePath, methodNameAndSig, lineData.getLineNumber(), NodeType.SWITCH_BRANCH);
//										Log.out(this, "%s, SD: %d", lineIdentifier.toString(), switchData.getDefaultHits());
//										onNewLine(
//												packageName, sourceFilePath, methodIdentifier, lineIdentifier, lineSpectra, trace,
//												fullSpectra, switchData.getDefaultHits());
										
										long switchBranchHits = 0;
										for (int i = 0; i < switchData.getNumberOfValidBranches(); ++i) {
											switchBranchHits += (switchData.getHits(i) < 0 ? 0 : switchData.getHits(i));
										}
										
//										// add nodes for default branch of switch statements
//										lineIdentifier = getIdentifier(
//												packageName, sourceFilePath, methodNameAndSig, lineData.getLineNumber(), NodeType.SWITCH_BRANCH);
//										Log.out(this, "%s, SH: %d, SD: %d", lineIdentifier4.toString(), switchBranchHits, switchData.getDefaultHits());
//										onNewLine(
//												packageName, sourceFilePath, methodIdentifier, lineIdentifier, lineSpectra, trace,
//												fullSpectra, switchBranchHits);
										
										onNewLine(
												packageName, sourceFilePath, methodIdentifier, lineIdentifier4, lineSpectra, trace,
												fullSpectra, switchBranchHits + switchData.getDefaultHits());
									}
								}
							}
						}

						onLeavingMethod(packageName, sourceFilePath, methodIdentifier, lineSpectra, trace);
					}

					onLeavingClass(packageName, sourceFilePath, lineSpectra, trace);
				}
			}

			onLeavingPackage(packageName, lineSpectra, trace);
		}

		if (!coveredLines) {
			Log.warn(this, "Test '%s' covered no lines.", testId);
			if (projectData.getExecutionTraces() == null) {
				Log.err(this, "Execution trace is null for test '%s'.", testId);
				return false;
			}
			if (!projectData.getExecutionTraces().isEmpty()) {
				Log.err(this, "Execution trace for test '%s' is NOT empty.", testId);
				return false;
			}
			return true;
		}
		
		if (projectData.getExecutionTraces() == null) {
			Log.err(this, "Execution trace is null for test '%s'.", testId);
			return false;
		}

		if (projectData.getExecutionTraces().isEmpty()) {
			Log.warn(this, "No execution trace for test '%s'.", testId);
		} else {
			// TODO debug output
			// for (Object classData : projectData.getClasses()) {
			// Log.out(true, this, ((MyClassData)classData).getName());
			// }
//			Log.out(true, this, "Trace: " + reportWrapper.getIdentifier());
			
			
			// convert execution traces from statement sequences to sequences of sub traces
			Map<Long, EfficientCompressedIntegerTrace> executionTracesWithSubTraces = new HashMap<>();
			for (Iterator<Entry<Long, EfficientCompressedIntegerTrace>> iterator = projectData.getExecutionTraces().entrySet().iterator(); iterator.hasNext();) {
				Entry<Long, EfficientCompressedIntegerTrace> entry = iterator.next();
				
				EfficientCompressedIntegerTrace mappedExecutionTrace = generateSubTraceExecutionTrace(entry.getValue(), projectData);
				
				executionTracesWithSubTraces.put(entry.getKey(), mappedExecutionTrace);
			}
			projectData.addExecutionTraces(executionTracesWithSubTraces);
			
			
			int threadId = -1;
			for (Iterator<Entry<Long, EfficientCompressedIntegerTrace>> iterator = projectData.getExecutionTraces().entrySet().iterator(); iterator.hasNext();) {
				Entry<Long, EfficientCompressedIntegerTrace> entry = iterator.next();
				++threadId;
				
				
				boolean printTraces = 
						false;
//						true;
				if (printTraces) {
					// only for testing... the arrays in a trace are composed of [ class id, counter id].
					String[] idToClassNameMap = projectData.getIdToClassNameMap();
					Log.out(true, this, "Thread: " + entry.getKey());
					// iterate over executed statements in the trace
					IntTraceIterator traceIterator = entry.getValue().iterator();
					while (traceIterator.hasNext()) {
						Integer subTraceId = traceIterator.next();
						EfficientCompressedIntegerTrace subTrace = null;
						if (subTraceId == 0) {
							subTrace = null;
						} else {
							subTrace = existingSubTraces.get(subTraceId);
							if (subTrace == null) {
								Log.err(this, "No sub trace found for ID: " + subTraceId);
								return false;
							}
						}
						if (subTrace == null) {
							continue;
						}
						Log.out(true, this, "sub trace ID: " + subTraceId + ", length: " + subTrace.size());

						IntTraceIterator longTraceIterator = subTrace.iterator();
						while (longTraceIterator.hasNext()) {
							int statement = longTraceIterator.next();
//							if (CoberturaStatementEncoding.getClassId(statement) != 142) {
//								continue;
//							}
//							Log.out(true, this, "statement: " + Arrays.toString(statement));
							// TODO store the class names with '.' from the beginning, or use the '/' version?
							String classSourceFileName = idToClassNameMap[CoberturaStatementEncoding.getClassId(statement)];
							if (classSourceFileName == null) {
								//						throw new IllegalStateException("No class name found for class ID: " + statement[0]);
								Log.err(this, "No class name found for class ID: " + CoberturaStatementEncoding.getClassId(statement));
								return false;
							}
//							if (!classSourceFileName.contains("FastDateParser")) {
//								continue;
//							}
							ClassData classData = projectData.getClassData(classSourceFileName.replace('/', '.'));

							if (classData != null) {
								if (classData.getCounterId2LineNumbers() == null) {
									Log.err(this, "No counter ID to line number map for class " + classSourceFileName);
									return false;
								}
								int[] lineNumber = classData.getCounterId2LineNumbers()[CoberturaStatementEncoding.getCounterId(statement)];
//								if (lineNumber != 398 && lineNumber != 399) {
//									continue;
//								}

								NodeType nodeType = NodeType.NORMAL;
								// these following lines print out the execution trace
								String addendum = "";
								if (lineNumber[1] != CoberturaStatementEncoding.NORMAL_ID) {
									switch (lineNumber[1]) {
									case CoberturaStatementEncoding.BRANCH_ID:
										addendum = " (from branch/'false' branch)";
										nodeType = NodeType.FALSE_BRANCH;
										break;
									case CoberturaStatementEncoding.JUMP_ID:
										addendum = " (after jump/'true' branch)";
										nodeType = NodeType.TRUE_BRANCH;
										break;
									case CoberturaStatementEncoding.SWITCH_ID:
										addendum = " (after switch label)";
										nodeType = NodeType.SWITCH_BRANCH;
										break;
									default:
										addendum = " (unknown)";
									}
								}
								Log.out(true, this, classSourceFileName + ", counter ID " + CoberturaStatementEncoding.getCounterId(statement) +
										", line " + (lineNumber[0] < 0 ? "(not set)" : String.valueOf(lineNumber[0])) +
										addendum);

								// the array is initially set to -1 to indicate counter IDs that were not set, if any
								if (lineNumber[0] >= 0) {
									int nodeIndex = getNodeIndex(classData.getSourceFileName(), lineNumber[0], nodeType);
									//								nodeIndex = 1;
									if (nodeIndex >= 0) {
										// everything's fine; the below statement is from an earlier version
										//traceOfNodeIDs.add(nodeIndex);
									} else {
										// node index not correct...
										String throwAddendum = "";
										if (lineNumber[1] != CoberturaStatementEncoding.NORMAL_ID) {
											switch (lineNumber[1]) {
											case CoberturaStatementEncoding.BRANCH_ID:
												throwAddendum = " (from branch/'false' branch)";
												break;
											case CoberturaStatementEncoding.JUMP_ID:
												throwAddendum = " (after jump/'true' branch)";
												break;
											case CoberturaStatementEncoding.SWITCH_ID:
												throwAddendum = " (after switch label)";
												break;
											default:
												throwAddendum = " (unknown)";
											}
										}
										Log.err(this, "Node not found in spectra: "
												+ classData.getSourceFileName() + ":" + lineNumber[0] 
												+ " from counter id " + CoberturaStatementEncoding.getCounterId(statement) + throwAddendum);
										//									return false;
									}
//								} else if (statement.length <= 2 || statement[2] != 0) {
//									// disregard counter ID 0 if it comes from an internal variable (fake jump?!)
//									// this should actually not be an issue anymore!
//									//							throw new IllegalStateException("No line number found for counter ID: " + counterId
//									//									+ " in class: " + classData.getName());
//									Log.err(this, "No line number found for counter ID: " + CoberturaStatementEncoding.getCounterId(statement)
//									+ " in class: " + classData.getName());
//									return false;
								} else {
									Log.err(this, "No line number found for counter ID: " + CoberturaStatementEncoding.getCounterId(statement)
									+ " in class: " + classData.getName());
									return false;
									//							// we have to add a dummy node here to not mess up the repetition markers
									//							traceOfNodeIDs.add(-1);
									//							Log.out(this, "Ignoring counter ID: " + statement[1]
									//									+ " in class: " + classData.getName());
								}
							} else {
								throw new IllegalStateException("Class data for '" + classSourceFileName + "' not found.");
							}
						}
					}
					System.out.flush();
					//Thread.sleep(1000);
					// testing done!....
				}
				
//				// only for testing... the arrays in a trace are composed of [ class id, counter id].
//				String[] idToClassNameMap = projectData.getIdToClassNameMap();
////				Log.out(true, this, "Thread: " + entry.getKey());
//				// iterate over statements in the sub traces
//				for (Iterator<Entry<Integer, CompressedLongTraceBase>> subTraceIterator = idToSubtraceMap.entrySet().iterator(); 
//						subTraceIterator.hasNext();) {
//					
//					CompressedLongTraceBase subTrace = subTraceIterator.next().getValue();
////					Log.out(true, this, "sub trace ID: " + subTraceId + ", length: " + subTrace.size());
////					if (subTraceId >= 0) {
////						continue;
////					}
//					ReplaceableCloneableLongIterator statementIterator = subTrace.getCompressedTrace().iterator();
//					while (statementIterator.hasNext()) {
//						long encodedStatement = statementIterator.next();
//						int classId = CoberturaStatementEncoding.getClassId(encodedStatement);
//						int counterId = CoberturaStatementEncoding.getCounterId(encodedStatement);
//						int specialIndicatorId = CoberturaStatementEncoding.getSpecialIndicatorId(encodedStatement);
//						//					 Log.out(true, this, "statement: " + Arrays.toString(statement));
//						// TODO store the class names with '.' from the beginning, or use the '/' version?
//						String classSourceFileName = idToClassNameMap[classId];
//						if (classSourceFileName == null) {
//							//						throw new IllegalStateException("No class name found for class ID: " + classId);
//							Log.err(this, "No class name found for class ID: " + classId);
//							return false;
//						}
//						ClassData classData = projectData.getClassData(classSourceFileName.replace('/', '.'));
//
//						if (classData != null) {
//							if (classData.getCounterId2LineNumbers() == null) {
//								Log.err(this, "No counter ID to line number map for class " + classSourceFileName);
//								return false;
//							}
//							int lineNumber = classData.getCounterId2LineNumbers()[counterId];
//
//							NodeType nodeType = NodeType.NORMAL;
//							// these following lines print out the execution trace
//							switch (specialIndicatorId) {
//							case CoberturaStatementEncoding.BRANCH_ID:
//								nodeType = NodeType.FALSE_BRANCH;
//								break;
//							case CoberturaStatementEncoding.JUMP_ID:
//								nodeType = NodeType.TRUE_BRANCH;
//								break;
//							default:
//								// ignore switch statements...
//							}
////							Log.out(true, this, classSourceFileName + ", counter ID " + statement[1] +
////									", line " + (lineNumber < 0 ? "(not set)" : String.valueOf(lineNumber)) +
////									addendum);
//
//							// the array is initially set to -1 to indicate counter IDs that were not set, if any
//							if (lineNumber >= 0) {
//								int nodeIndex = getNodeIndex(classData.getSourceFileName(), lineNumber, nodeType);
//								if (nodeIndex >= 0) {
//									// everything's fine; the below statement is from an earlier version
//									//traceOfNodeIDs.add(nodeIndex);
//								} else {
//									// node index not correct...
//									String throwAddendum = "";
//									switch (specialIndicatorId) {
//									case CoberturaStatementEncoding.BRANCH_ID:
//										throwAddendum = " (from branch/'false' branch)";
//										break;
//									case CoberturaStatementEncoding.JUMP_ID:
//										throwAddendum = " (after jump/'true' branch)";
//										break;
//									case CoberturaStatementEncoding.SWITCH_ID:
//										throwAddendum = " (after switch label)";
//										break;
//									default:
//										// ?
//									}
//									Log.err(this, testId +": Node not found in spectra: "
//											+ classData.getSourceFileName() + ":" + lineNumber 
//											+ " from counter id " + counterId + throwAddendum);
//									return false;
////									if (nodeType.equals(NodeType.NORMAL)) {
////										// ignore branch nodes that are erroneous for some reason...
////										return false;
////									}
//								}
//							} else {
//								Log.err(this, "No line number found for counter ID: " + counterId
//										+ " in class: " + classData.getName());
//								return false;
//								//							// we have to add a dummy node here to not mess up the repetition markers
//								//							traceOfNodeIDs.add(-1);
//								//							Log.out(this, "Ignoring counter ID: " + statement[1]
//								//									+ " in class: " + classData.getName());
//							}
//						} else {
//							throw new IllegalStateException("Class data for '" + classSourceFileName + "' not found.");
//						}
//					}
//				}
//				System.out.flush();
//				//Thread.sleep(1000);
//				//
				
				// collect the raw trace for future compression, etc.
				// this will, among others, extract common sequences for added traces
				entry.getValue().deleteOnExit();
				traceCollector.addRawTraceToPool(traceCount, threadId, 
						entry.getValue(), existingSubTraces);
				// processed and done with...
				iterator.remove();
				
				
//
//				compressedTrace = null;
//				// this only takes the compressed trace array that was based on the original input trace;
//				// multiple statements/touch points in the input trace may be mapped to the same node!
//				// we reuse the repetition markers from the input trace here!
//				ExecutionTrace eTrace = new ExecutionTrace(traceOfNodeIDs, entry.getValue());
//				// throw away not needed input traces (memory...)
//				iterator.remove();
//				
////				Log.out(this, "Test: " + testId);
////				Log.out(this, Arrays.toString(eTrace.reconstructFullTrace()));
////				Log.out(this, Arrays.toString(eTrace.getCompressedTrace()));
////				Log.out(this, Arrays.toString(eTrace.getRepetitionMarkers()));
////				if (eTrace.getChild() != null) {
////					Log.out(this, Arrays.toString(eTrace.getChild().getRepetitionMarkers()));
////				}
//				
//				// add the execution trace to the coverage trace and, thus, to the spectra
////				 trace.addExecutionTrace(traceOfNodeIDs);
//				// collect the raw trace for future compression, etc.
//				// this will, among others, extract common sequences for added traces
//				traceCollector.addRawTraceToPool(traceCount, threadId, eTrace);
//				traceOfNodeIDs = null;
			}
			
//			for (Entry<Integer, EfficientCompressedIntegerTrace> entry : existingSubTraces.entrySet()) {
//				entry.getValue().deleteOnExit();
//				entry.getValue().deleteIfMarked();
//			}
		}
		
		return true;
		} catch (Throwable e) {
			Log.err(this, e, "Exception thrown for test '%s'.", testId);
			return false;
		}
	}

	private EfficientCompressedIntegerTrace generateSubTraceExecutionTrace(EfficientCompressedIntegerTrace trace, ProjectData projectData) {
		EfficientCompressedIntegerTrace resultTrace = new EfficientCompressedIntegerTrace(
				trace.getCompressedTrace().getOutputDir(), trace.getCompressedTrace().getFilePrefix(),
				ExecutionTraceCollector.EXECUTION_TRACE_CHUNK_SIZE, ExecutionTraceCollector.MAP_CHUNK_SIZE, true, true);
		String[] idToClassNameMap = projectData.getIdToClassNameMap();
		// iterate over trace and generate new trace based on seen sub traces
		// iterate over executed statements in the trace
		IntTraceIterator traceIterator = trace.iterator();
		
		EfficientCompressedIntegerTrace currentSubTrace = getNewSubTrace(trace);
		String lastMethod = "";
		NodeType lastNodeType = NodeType.NORMAL;
		while (traceIterator.hasNext()) {
			int statement = traceIterator.next();
			
//			// check if the current statement indicates a catch block entry
//			if (traceIterator.hasNext() && traceIterator.peek() == ExecutionTraceCollector.CATCH_BLOCK_ID && !currentSubTrace.isEmpty()) {
//				// skip the indicator
//				traceIterator.next();
//				// cut the trace **before** each catch block entry
//				currentSubTrace = processLastSubTrace(trace, resultTrace, currentSubTrace);
//			}
			
			// check if the current statement indicates a catch block entry
			if (statement == ExecutionTraceCollector.CATCH_BLOCK_ID) {
				// cut the trace **before** each catch block entry
				currentSubTrace = processLastSubTrace(trace, resultTrace, currentSubTrace, lastNodeType);
				// skip the indicator
				if (traceIterator.hasNext()) {
					statement = traceIterator.next();
				} else {
					continue;
				}
			}

//			Log.out(true, this, "statement: " + Arrays.toString(statement));
			// TODO store the class names with '.' from the beginning, or use the '/' version?
			String classSourceFileName = idToClassNameMap[CoberturaStatementEncoding.getClassId(statement)];
			if (classSourceFileName == null) {
//				throw new IllegalStateException("No class name found for class ID: " + statement[0]);
				Log.err(this, "No class name found for class ID: " + CoberturaStatementEncoding.getClassId(statement));
				return null;
			}
			ClassData classData = projectData.getClassData(classSourceFileName.replace('/', '.'));

			if (classData != null) {
				if (classData.getCounterId2LineNumbers() == null) {
					Log.err(this, "No counter ID to line number map for class " + classSourceFileName);
					return null;
				}
				int[] lineNumber = classData.getCounterId2LineNumbers()[CoberturaStatementEncoding.getCounterId(statement)];
				//			if (lineNumber != 398 && lineNumber != 399) {
				//				continue;
				//			}

				// check if we switched to a different method than we were in before;
				// this should allow for the sub traces to be uniquely determined by first and last statement
				String currentMethod = classData.getMethodNameAndDescriptor(lineNumber[0]);
				if (!currentMethod.equals(lastMethod) && !currentSubTrace.isEmpty()) {
					// cut the trace after each change in methods
					currentSubTrace = processLastSubTrace(trace, resultTrace, currentSubTrace, lastNodeType);
				}
				lastMethod = currentMethod;
				
				// add the current statement to the current sub trace
				currentSubTrace.add(statement);
				
				NodeType nodeType = NodeType.NORMAL;
				if (lineNumber[1] != CoberturaStatementEncoding.NORMAL_ID) {
					switch (lineNumber[1]) {
					case CoberturaStatementEncoding.BRANCH_ID:
						nodeType = NodeType.FALSE_BRANCH;
						break;
					case CoberturaStatementEncoding.JUMP_ID:
						nodeType = NodeType.TRUE_BRANCH;
						break;
					case CoberturaStatementEncoding.SWITCH_ID:
						nodeType = NodeType.SWITCH_BRANCH;
						break;
					default:
					}
				}

				lastNodeType = nodeType;
				
				if (!nodeType.equals(NodeType.NORMAL) && !currentSubTrace.isEmpty()) {
					// cut the trace after each branching statement
					currentSubTrace = processLastSubTrace(trace, resultTrace, currentSubTrace, nodeType);
				}

			} else {
				throw new IllegalStateException("Class data for '" + classSourceFileName + "' not found.");
			}
		}
		
		// process any remaining statements
		if (!currentSubTrace.isEmpty()) {
			currentSubTrace = processLastSubTrace(trace, resultTrace, currentSubTrace, lastNodeType);
			currentSubTrace.clear();
		}
		
		trace.clear();

		return resultTrace;
	}

	private EfficientCompressedIntegerTrace processLastSubTrace(EfficientCompressedIntegerTrace trace,
			EfficientCompressedIntegerTrace resultTrace, EfficientCompressedIntegerTrace currentSubTrace, NodeType lastNodeType) {
		// get a representation id for the subtrace (unique for sub traces that start and end within the same method!)
		long subTraceId = CoberturaStatementEncoding.generateRepresentationForSubTrace(currentSubTrace);
		Integer id = idToSubtraceIdMap.get(subTraceId);

//		// check if the current sub trace has been seen before and, if so, get the respective id
//		for (EfficientCompressedIntegerTrace subTrace : subTraces) {
//			if (sameSubTrace(subTrace, currentSubTrace)) {
//				id = subtraceToIdMap.get(subTrace);
//				break;
//			}
//		}
		
		Integer lastExecutedStatement = null;
		if (!currentSubTrace.isEmpty() && !lastNodeType.equals(NodeType.NORMAL)) {
			lastExecutedStatement = currentSubTrace.getLastElement();
		}
		
		if (id == null) {
			// first time seeing this sub trace!
			// starts with id 1
			id = ++currentId;

			// add id to the map
			idToSubtraceIdMap.put(subTraceId, id);
			// add sub trace to the list of existing sub traces (together with the id)
			existingSubTraces.put(id, currentSubTrace);
			currentSubTrace.sleep();
			
			currentSubTrace = getNewSubTrace(trace);
		} else {
			// already seen, so reuse the sub trace
			currentSubTrace.clear();
//			currentSubTrace = getNewSubTrace(trace);
		}
		
		if (lastExecutedStatement != null) {
			// add the last executed statement (usually a branch)
			// to the new sub trace
			currentSubTrace.add(lastExecutedStatement);
		}
		
		resultTrace.add(id);
		return currentSubTrace;
	}

//	private boolean sameSubTrace(EfficientCompressedIntegerTrace subTrace,
//			EfficientCompressedIntegerTrace currentSubTrace) {
//		if (subTrace.size() != currentSubTrace.size() || 
//				subTrace.getCompressedTrace().size() != currentSubTrace.getCompressedTrace().size()) {
//			return false;
//		}
//		IntTraceIterator iterator = subTrace.iterator();
//		IntTraceIterator iterator2 = currentSubTrace.iterator();
//		while (iterator.hasNext()) {
//			if (iterator.next() != iterator2.next()) {
//				return false;
//			}
//		}
//		return true;
//	}

	private EfficientCompressedIntegerTrace getNewSubTrace(EfficientCompressedIntegerTrace trace) {
		return new EfficientCompressedIntegerTrace(
				trace.getCompressedTrace().getOutputDir(), trace.getCompressedTrace().getFilePrefix(),
				ExecutionTraceCollector.SUBTRACE_ARRAY_SIZE, ExecutionTraceCollector.MAP_CHUNK_SIZE, true);
	}

	public void addExecutionTracesToSpectra(ISpectra<SourceCodeBlock, ? super K> spectra) {
		// gets called after ALL tests have been processed
		Log.out(TraceCoberturaReportLoader.class, "Generating sequence index...");
		try {
			traceCollector.getIndexer().getSequences();
		} catch (UnsupportedOperationException e) {
			traceCollector.getIndexer().getMappedSequences();
		}

		Log.out(TraceCoberturaReportLoader.class, "Generating execution traces...");
		
		// generate execution traces from raw traces
		spectra.setRawTraceCollector(traceCollector);
				
		ProgressTracker tracker = new ProgressTracker(false);
		// iterate through the traces
		for (ITrace<?> trace : spectra.getTraces()) {
			tracker.track("mem: " + Runtime.getRuntime().freeMemory() + ", " + trace.getIdentifier());
			//				Runtime.getRuntime().gc();

			// execute to generate indexed execution traces;
			// they will get stored to disk and reloaded again, later
			// TODO When saving the spectra, it should only copy the byte array
			trace.getExecutionTraces();
		}
		
		if (!traceCollector.getIndexer().isIndexed()) {
			Log.out(TraceCoberturaReportLoader.class, "Generating sequence index (again, due to changes)...");
			try {
				traceCollector.getIndexer().getSequences();
			} catch (UnsupportedOperationException e) {
				traceCollector.getIndexer().getMappedSequences();
			}
		}
		
		Log.out(TraceCoberturaReportLoader.class, "Mapping counter IDs to line numbers...");
		
		// generate mapping from statements to spectra nodes
		SimpleIntIndexerCompressed simpleIndexer = new SimpleIntIndexerCompressed(
				traceCollector.getIndexer(), existingSubTraces, 
				spectra, projectData);
		
		// store the indexer with the spectra
		spectra.setIndexer(simpleIndexer);
		
		
		
//		// generate the execution traces for each test case and add them to the spectra;
//		// this needs to be done AFTER all tests have been executed
//		for (ITrace<?> trace : spectra.getTraces()) {
//			// generate execution traces from collected raw traces
//			List<ExecutionTrace> executionTraces = traceCollector.getExecutionTraces(trace.getIndex());
//			if (executionTraces != null) {
//				// add those traces to the test case
//				for (ExecutionTrace executionTrace : executionTraces) {
//					trace.addExecutionTrace(executionTrace);
//				}
//			}
//		}
//		
//		// remove temporary zip file containing the raw traces
//		try {
//			traceCollector.finalize();
//		} catch (Throwable e) {
//			// meh...
//		}
	}
	
}
