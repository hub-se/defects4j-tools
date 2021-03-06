package se.de.hu_berlin.informatik.gen.ranking;

import org.apache.commons.cli.Option;
import se.de.hu_berlin.informatik.faultlocalizer.IFaultLocalizer;
import se.de.hu_berlin.informatik.faultlocalizer.sbfl.FaultLocalizerFactory;
import se.de.hu_berlin.informatik.gen.ranking.modules.RankingFromTraceFileModule;
import se.de.hu_berlin.informatik.gen.ranking.modules.RankingModule;
import se.de.hu_berlin.informatik.spectra.core.ComputationStrategies;
import se.de.hu_berlin.informatik.spectra.core.INode;
import se.de.hu_berlin.informatik.spectra.core.SourceCodeBlock;
import se.de.hu_berlin.informatik.spectra.core.manipulation.BuildBlockSpectraModule;
import se.de.hu_berlin.informatik.spectra.core.manipulation.FilterSpectraModule;
import se.de.hu_berlin.informatik.spectra.core.manipulation.ReadSpectraModule;
import se.de.hu_berlin.informatik.spectra.core.manipulation.RemoveTestClassNodesFromSpectraModule;
import se.de.hu_berlin.informatik.spectra.util.Indexable;
import se.de.hu_berlin.informatik.spectra.util.TraceFileModule;
import se.de.hu_berlin.informatik.utils.files.FileUtils;
import se.de.hu_berlin.informatik.utils.miscellaneous.Log;
import se.de.hu_berlin.informatik.utils.optionparser.OptionParser;
import se.de.hu_berlin.informatik.utils.optionparser.OptionWrapper;
import se.de.hu_berlin.informatik.utils.optionparser.OptionWrapperInterface;
import se.de.hu_berlin.informatik.utils.processors.sockets.module.ModuleLinker;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


/**
 * Computes SBFL rankings or hit traces from an existing spectra file
 * with the support of the stardust API.
 *
 * @author Simon Heiden
 */
final public class Spectra2Ranking {

    private Spectra2Ranking() {
        //disallow instantiation
    }

    public enum CmdOptions implements OptionWrapperInterface {
        /* add options here according to your needs */
        INPUT("i", "input", true, "A compressed spectra file.", true),
        OUTPUT("o", "output", true, "Path to output directory.", true),
        FILTER("f", "filterNodes", false, "Whether to remove irrelevant nodes, i.e. "
                + "nodes that were not touched by any failing trace.", false),
        REMOVE_TEST_CLASSES("r", "removeTestClasses", false, "Whether to remove nodes that are part of a test class.", false),
        CONDENSE("c", "condenseNodes", false, "Whether to combine several lines "
                + "with equal trace involvement to larger blocks.", false),
        SIMILARITY_SBFL("sim", "similarity", false, "Whether the ranking should be based on similarity between traces.", false),
        LOCALIZERS(Option.builder("l").longOpt("localizers").optionalArg(true)
                .hasArgs().desc("A list of identifiers of Cobertura localizers "
                        + "(e.g. 'Tarantula', 'Jaccard', ...).").build());

        /* the following code blocks should not need to be changed */
        final private OptionWrapper option;

        //adds an option that is not part of any group
        CmdOptions(final String opt, final String longOpt,
                   final boolean hasArg, final String description, final boolean required) {
            this.option = new OptionWrapper(
                    Option.builder(opt).longOpt(longOpt).required(required).
                            hasArg(hasArg).desc(description).build(), NO_GROUP);
        }

        //adds an option that is part of the group with the specified index (positive integer)
        //a negative index means that this option is part of no group
        //this option will not be required, however, the group itself will be
        CmdOptions(final String opt, final String longOpt,
                   final boolean hasArg, final String description, final int groupId) {
            this.option = new OptionWrapper(
                    Option.builder(opt).longOpt(longOpt).required(false).
                            hasArg(hasArg).desc(description).build(), groupId);
        }

        //adds the given option that will be part of the group with the given id
        CmdOptions(final Option option, final int groupId) {
            this.option = new OptionWrapper(option, groupId);
        }

        //adds the given option that will be part of no group
        CmdOptions(final Option option) {
            this(option, NO_GROUP);
        }

        @Override
        public String toString() {
            return option.getOption().getOpt();
        }

        @Override
        public OptionWrapper getOptionWrapper() {
            return option;
        }
    }

    /**
     * @param args -i input-file [-l loc1 loc2 ...] -o output
     */
    public static void main(final String[] args) {

        final OptionParser options = OptionParser.getOptions("Spectra2Ranking", false, CmdOptions.class, args);

        generateRanking(options.getOptionValue(CmdOptions.INPUT),
                options.getOptionValue(CmdOptions.OUTPUT),
                options.getOptionValues(CmdOptions.LOCALIZERS),
                options.hasOption(CmdOptions.FILTER),
                options.hasOption(CmdOptions.REMOVE_TEST_CLASSES),
                options.hasOption(CmdOptions.CONDENSE),
                options.hasOption(CmdOptions.SIMILARITY_SBFL)
                        ? ComputationStrategies.SIMILARITY_FL : ComputationStrategies.STANDARD_SBFL,
                null);

    }

    /**
     * Convenience method. Generates a ranking from the given spectra file. Checks the inputs
     * for correctness and aborts the application with an error message if one of
     * the inputs is not correct.
     *
     * @param spectraFileOption     a compressed spectra file
     * @param rankingDir            path to the main ranking directory
     * @param localizers            an array of String representation of fault localizers
     *                              as used by STARDUST
     * @param removeIrrelevantNodes whether to remove nodes that were not touched by any failed traces
     * @param removeTestClassNodes  whether to remove nodes that are part of test classes
     * @param condenseNodes         whether to combine several lines with equal trace involvement
     * @param strategy              the strategy to use for computation of the rankings
     * @param suffix                a suffix to append to generated trace files and metrics files
     */
    public static void generateRanking(
            final String spectraFileOption, final String rankingDir, final String[] localizers,
            final boolean removeIrrelevantNodes, final boolean removeTestClassNodes, 
            final boolean condenseNodes, ComputationStrategies strategy, String suffix) {
        final Path spectraFile = FileUtils.checkIfAnExistingFile(null, spectraFileOption);
        if (spectraFile == null) {
            Log.abort(Spectra2Ranking.class, "'%s' is not an existing file.", spectraFileOption);
        }
        final Path outputDir = FileUtils.checkIfNotAnExistingFile(null, rankingDir);
        if (outputDir == null) {
            Log.abort(Spectra2Ranking.class, "'%s' is not a directory.", rankingDir);
        }
        if (localizers == null) {
            Log.abort(Spectra2Ranking.class, "No localizers given.");
        }
        generateRankingForCheckedInputs(spectraFile, outputDir.toString(),
                localizers, removeIrrelevantNodes, removeTestClassNodes, condenseNodes, strategy, suffix);
    }

    /**
     * Generates the ranking. Assumes that inputs have been checked to be correct.
     *
     * @param spectraFile           a compressed spectra file
     * @param outputDir             path to the main ranking directory
     * @param localizers            an array of String representation of fault localizers
     *                              as used by STARDUST
     * @param removeIrrelevantNodes whether to remove nodes that were not touched by any failed traces
     * @param removeTestClassNodes  whether to remove nodes that are part of test classes
     * @param condenseNodes         whether to combine several lines with equal trace involvement
     * @param strategy              the strategy to use for computation of the rankings
     * @param suffix                a suffix to append to generated trace files and metrics files
     */
    private static void generateRankingForCheckedInputs(final Path spectraFile,
                                                        final String outputDir, final String[] localizers,
                                                        final boolean removeIrrelevantNodes,
                                                        final boolean removeTestClassNodes, 
                                                        final boolean condenseNodes, 
                                                        ComputationStrategies strategy, String suffix) {
        ModuleLinker linker = new ModuleLinker()
                .append(new ReadSpectraModule<>(SourceCodeBlock.DUMMY));
        if (condenseNodes) {
            linker.append(new BuildBlockSpectraModule());
        }
        if (removeIrrelevantNodes) {
            linker.append(new FilterSpectraModule<SourceCodeBlock>(INode.CoverageType.EF_EQUALS_ZERO));
        }
        if (removeTestClassNodes) {
        	linker.append(new RemoveTestClassNodesFromSpectraModule<>(SourceCodeBlock.DUMMY));
        }
        linker.append(new TraceFileModule<SourceCodeBlock>(Paths.get(outputDir), suffix));
        linker.append(new RankingModule<SourceCodeBlock>(strategy, outputDir, localizers))
                .submit(spectraFile);
    }

    /**
     * Convenience method. Generates a ranking from the given spectra file. Checks the inputs
     * for correctness and aborts the application with an error message if one of
     * the inputs is not correct.
     *
     * @param dummy                 dummy identifier; used for loading the spectra
     * @param spectraFileOption     a compressed spectra file
     * @param rankingDir            path to the main ranking directory
     * @param localizers            an array of String representation of fault localizers
     *                              as used by STARDUST
     * @param removeIrrelevantNodes whether to remove nodes that were not touched by any failed traces
     * @param removeTestClassNodes  whether to remove nodes that are part of test classes
     * @param strategy              the strategy to use for computation of the rankings
     * @param suffix                a suffix to append to generated trace files and metrics files
     * @param <T>                   type of node identifiers
     */
    public static <T extends Indexable<T> & Comparable<T>> void generateRanking(T dummy,
    		final String spectraFileOption, final String rankingDir, final String[] localizers,
    		final boolean removeIrrelevantNodes, final boolean removeTestClassNodes, 
    		ComputationStrategies strategy, String suffix) {
    	final Path spectraFile = FileUtils.checkIfAnExistingFile(null, spectraFileOption);
        if (spectraFile == null) {
            Log.abort(Spectra2Ranking.class, "'%s' is not an existing file.", spectraFileOption);
        }
        final Path outputDir = FileUtils.checkIfNotAnExistingFile(null, rankingDir);
        if (outputDir == null) {
            Log.abort(Spectra2Ranking.class, "'%s' is not a directory.", rankingDir);
        }
        if (localizers == null) {
            Log.abort(Spectra2Ranking.class, "No localizers given.");
        }
        generateRankingForCheckedInputs(dummy, spectraFile, outputDir.toString(),
                localizers, removeIrrelevantNodes, removeTestClassNodes, strategy, suffix);
    }

    /**
     * Generates the ranking. Assumes that inputs have been checked to be correct.
     *
     * @param dummy                 dummy identifier; used for loading the spectra
     * @param spectraFile           a compressed spectra file
     * @param outputDir             path to the main ranking directory
     * @param localizers            an array of String representation of fault localizers
     *                              as used by STARDUST
     * @param removeIrrelevantNodes whether to remove nodes that were not touched by any failed traces
     * @param removeTestClassNodes  whether to remove nodes that are part of test classes
     * @param strategy              the strategy to use for computation of the rankings
     * @param suffix                a suffix to append to generated trace files and metrics files
     * @param <T>                   type of node identifiers
     */
    private static <T extends Indexable<T> & Comparable<T>> void generateRankingForCheckedInputs(
    		T dummy, final Path spectraFile,
    		final String outputDir, final String[] localizers, 
    		final boolean removeIrrelevantNodes, final boolean removeTestClassNodes, 
    		ComputationStrategies strategy, String suffix) {
    	ModuleLinker linker = new ModuleLinker()
                .append(new ReadSpectraModule<T>(dummy));
        if (removeIrrelevantNodes) {
            linker.append(new FilterSpectraModule<T>(INode.CoverageType.EF_EQUALS_ZERO));
        }
        if (removeTestClassNodes) {
        	linker.append(new RemoveTestClassNodesFromSpectraModule<T>(dummy));
        }
        linker.append(new TraceFileModule<T>(Paths.get(outputDir), suffix));
        linker.append(new RankingModule<T>(strategy, outputDir, localizers))
                .submit(spectraFile);
    }


    /**
     * Convenience method. Generates a ranking from the given trace file. Checks the inputs
     * for correctness and aborts the application with an error message if one of
     * the inputs is not correct.
     *
     * @param dummy       dummy node identifier
     * @param traceFile   a trace file
     * @param metricsFile a metrics file
     * @param rankingDir  path to the main ranking directory
     * @param localizers  an array of String representation of fault localizers
     *                    as used by STARDUST
     * @param strategy    the strategy to use for computation of the rankings
     * @param <T>         the type of node identifiers
     */
    public static <T extends Indexable<T> & Comparable<T>> void generateRankingFromTraceFile(
            T dummy, final String traceFile, final String metricsFile,
            final String rankingDir, final String[] localizers,
            ComputationStrategies strategy) {
        generateRankingFromTraceFileForLocalizers(dummy, traceFile,
                metricsFile, rankingDir, getLocalizers(localizers), strategy);
    }


    /**
     * Convenience method. Generates a ranking from the given trace file. Checks the inputs
     * for correctness and aborts the application with an error message if one of
     * the inputs is not correct.
     *
     * @param dummy       dummy identifier
     * @param traceFile   a trace file
     * @param metricsFile a metrics file
     * @param rankingDir  path to the main ranking directory
     * @param localizers  a list of fault localizer instances
     *                    as used by STARDUST
     * @param strategy    the strategy to use for computation of the rankings
     * @param <T>         the type of nodes
     */
    public static <T extends Indexable<T> & Comparable<T>> void generateRankingFromTraceFileForLocalizers(
            T dummy, final String traceFile, final String metricsFile,
            final String rankingDir, final List<IFaultLocalizer<T>> localizers,
            ComputationStrategies strategy) {
        final Path traceFilePath = FileUtils.checkIfAnExistingFile(null, traceFile);
        if (traceFilePath == null) {
            Log.abort(Spectra2Ranking.class, "'%s' is not an existing file.", traceFile);
        }
        final Path metricsFilePath = FileUtils.checkIfAnExistingFile(null, metricsFile);
        if (metricsFilePath == null) {
            Log.abort(Spectra2Ranking.class, "'%s' is not an existing file.", metricsFile);
        }
        final Path outputDir = FileUtils.checkIfNotAnExistingFile(null, rankingDir);
        if (outputDir == null) {
            Log.abort(Spectra2Ranking.class, "'%s' is not a directory.", rankingDir);
        }
        if (localizers == null) {
            Log.abort(Spectra2Ranking.class, "No localizers given.");
        }

        new RankingFromTraceFileModule(dummy, traceFilePath, metricsFilePath,
                strategy, outputDir.toString())
                .submit(localizers);
    }

    public static <T> List<IFaultLocalizer<T>> getLocalizers(String[] localizerArray, String... without) {
        List<IFaultLocalizer<T>> localizers;
        if (localizerArray == null) {
            localizers = new ArrayList<>(0);
        } else {
            localizers = new ArrayList<>(localizerArray.length);

            //check if the given localizers can be found and abort in the negative case
            for (String s : localizerArray) {
                boolean skip = false;
                for (String exclude : without) {
                    if (s.toLowerCase(Locale.getDefault()).equals(exclude.toLowerCase(Locale.getDefault()))) {
                        skip = true;
                        break;
                    }
                }
                if (!skip) {
                    try {
                        localizers.add(FaultLocalizerFactory.newInstance(s));
                    } catch (IllegalArgumentException e) {
                        Log.abort(Spectra2Ranking.class, e, "Could not find localizer '%s'.", s);
                    }
                } else {
                    Log.out(Spectra2Ranking.class, "skipped %s.", s);
                }
            }
        }
        return localizers;
    }

}
