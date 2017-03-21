/**
 * 
 */
package se.de.hu_berlin.informatik.stardust.spectra.manipulation;

import se.de.hu_berlin.informatik.stardust.spectra.INode;
import se.de.hu_berlin.informatik.stardust.spectra.ISpectra;
import se.de.hu_berlin.informatik.utils.miscellaneous.Log;
import se.de.hu_berlin.informatik.utils.processors.AbstractProcessor;

/**
 * Reads a Spectra object and filters out all nodes that haven't been touched by
 * any failing trace. (EF == 0)
 * 
 * @author Simon Heiden
 * 
 * @param <T>
 * the type of nodes in the spectra
 */
public class FilterSpectraModule<T> extends AbstractProcessor<ISpectra<T>, ISpectra<T>> {

	public FilterSpectraModule() {
		super();
	}

	/* (non-Javadoc)
	 * @see se.de.hu_berlin.informatik.utils.tm.ITransmitter#processItem(java.lang.Object)
	 */
	@Override
	public ISpectra<T> processItem(final ISpectra<T> input) {
		Log.out(this, "Filtering spectra...");
		return input.removeNodesWithCoverageType(INode.CoverageType.EF_EQUALS_ZERO);
	}

}
