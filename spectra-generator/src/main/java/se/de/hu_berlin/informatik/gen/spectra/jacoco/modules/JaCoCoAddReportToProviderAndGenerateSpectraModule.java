/**
 *
 */
package se.de.hu_berlin.informatik.gen.spectra.jacoco.modules;

import se.de.hu_berlin.informatik.gen.spectra.jacoco.modules.sub.JaCoCoHitTraceModule;
import se.de.hu_berlin.informatik.junittestutils.data.StatisticsData;
import se.de.hu_berlin.informatik.spectra.core.ISpectra;
import se.de.hu_berlin.informatik.spectra.core.SourceCodeBlock;
import se.de.hu_berlin.informatik.spectra.provider.jacoco.JaCoCoSpectraProviderFactory;
import se.de.hu_berlin.informatik.spectra.provider.jacoco.report.JaCoCoReportProvider;
import se.de.hu_berlin.informatik.spectra.provider.jacoco.report.JaCoCoReportWrapper;
import se.de.hu_berlin.informatik.utils.miscellaneous.Log;
import se.de.hu_berlin.informatik.utils.processors.AbstractProcessor;
import se.de.hu_berlin.informatik.utils.statistics.StatisticsCollector;

/**
 *
 *
 * @author Simon Heiden
 */
public class JaCoCoAddReportToProviderAndGenerateSpectraModule extends AbstractProcessor<JaCoCoReportWrapper, ISpectra<SourceCodeBlock, ?>> {

    final private JaCoCoReportProvider<?> provider;
    private boolean saveFailedTraces = false;
    private JaCoCoHitTraceModule hitTraceModule = null;
    final StatisticsCollector<StatisticsData> statisticsContainer;
    private boolean errorState = false;

    public JaCoCoAddReportToProviderAndGenerateSpectraModule(
            final String failedTracesOutputDir, boolean fullSpectra,
            StatisticsCollector<StatisticsData> statisticsContainer) {
        super();
        this.provider = JaCoCoSpectraProviderFactory.getHitSpectraProvider(fullSpectra);
        this.statisticsContainer = statisticsContainer;
        if (failedTracesOutputDir != null) {
            this.saveFailedTraces = true;
            hitTraceModule = new JaCoCoHitTraceModule(failedTracesOutputDir);
        }
    }

    /* (non-Javadoc)
     * @see se.de.hu_berlin.informatik.utils.tm.ITransmitter#processItem(java.lang.Object)
     */
    @Override
    public ISpectra<SourceCodeBlock, ?> processItem(final JaCoCoReportWrapper reportWrapper) {

        if (reportWrapper == JaCoCoRunSingleTestAndReportModule.ERROR_WRAPPER) {
            errorState = true;
            return null;
        }

        if (saveFailedTraces && !reportWrapper.isSuccessful()) {
            hitTraceModule.submit(reportWrapper);
        }

        if (!provider.addData(reportWrapper)) {
            Log.err(this, "Could not add report '%s'.", reportWrapper.getIdentifier());
            errorState = true;
            throw new IllegalStateException("Adding a report failed. Can not provide correct spectra.");
        }

        return null;
    }

    @Override
    public ISpectra<SourceCodeBlock, ?> getResultFromCollectedItems() {
        if (errorState) {
            Log.err(this, "Providing the spectra failed.");
            return null;
        }

        try {
            ISpectra<SourceCodeBlock, ?> spectra = provider.loadSpectra();
            if (statisticsContainer != null && spectra != null) {
                statisticsContainer.addStatisticsElement(StatisticsData.NODES, spectra.getNodes().size());
//				for (INode<SourceCodeBlock> node : spectra.getNodes()) {
//					Log.out(this, "%s", node.getIdentifier());
//				}
            }
            return spectra;
        } catch (IllegalStateException e) {
            Log.err(this, e, "Providing the spectra failed.");
        }
        return null;
    }

}
