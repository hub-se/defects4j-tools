/*
 * This file is part of the "STARDUST" project.
 *
 * (c) Fabian Keller <hello@fabian-keller.de>
 *
 * For the full copyright and license information, please view the LICENSE
 * file that was distributed with this source code.
 */

package se.de.hu_berlin.informatik.stardust.localizer.sbfl.localizers;

import se.de.hu_berlin.informatik.stardust.localizer.sbfl.AbstractSpectrumBasedFaultLocalizer;
import se.de.hu_berlin.informatik.stardust.spectra.INode;

/**
 * M2 fault localizer $\frac{\EF}{\EF+\NP+2(\NF+\EP)}$
 * 
 * @param <T>
 *            type used to identify nodes in the system
 */
public class M2<T> extends AbstractSpectrumBasedFaultLocalizer<T> {

    /**
     * Create fault localizer
     */
    public M2() {
        super();
    }

    @Override
    public double suspiciousness(final INode<T> node) {
    	if (node.getEF() == 0) {
    		return 0;
    	}
        return (double)node.getEF()
                / (double)(node.getEF() + node.getNP() + 2.0d * (node.getNF() + node.getEP()));
    }

    @Override
    public String getName() {
        return "M2";
    }

}