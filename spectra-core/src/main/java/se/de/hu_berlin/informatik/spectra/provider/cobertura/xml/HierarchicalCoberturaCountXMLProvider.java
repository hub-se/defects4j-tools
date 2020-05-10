/*
 * This file is part of the "STARDUST" project. (c) Fabian Keller
 * <hello@fabian-keller.de> For the full copyright and license information,
 * please view the LICENSE file that was distributed with this source code.
 */

package se.de.hu_berlin.informatik.spectra.provider.cobertura.xml;

import se.de.hu_berlin.informatik.spectra.core.INode;
import se.de.hu_berlin.informatik.spectra.core.ISpectra;
import se.de.hu_berlin.informatik.spectra.core.Node.NodeType;
import se.de.hu_berlin.informatik.spectra.core.SourceCodeBlock;
import se.de.hu_berlin.informatik.spectra.core.count.CountTrace;
import se.de.hu_berlin.informatik.spectra.core.hit.HitSpectra;
import se.de.hu_berlin.informatik.spectra.provider.AbstractHierarchicalSpectraProvider;
import se.de.hu_berlin.informatik.spectra.provider.loader.ICoverageDataLoader;
import se.de.hu_berlin.informatik.spectra.provider.loader.cobertura.xml.HierarchicalCoberturaCountXMLLoader;

import java.io.File;

/**
 * Loads Cobertura reports to {@link HitSpectra} objects where each covered line
 * is represented by one node and each file represents one trace in the
 * resulting spectra.
 */
public class HierarchicalCoberturaCountXMLProvider<K extends CountTrace<SourceCodeBlock>>
        extends AbstractHierarchicalSpectraProvider<SourceCodeBlock, K, CoberturaCoverageWrapper> {

    private final ICoverageDataLoader<SourceCodeBlock, K, CoberturaCoverageWrapper> loader;

    public HierarchicalCoberturaCountXMLProvider(ISpectra<SourceCodeBlock, K> lineSpectra, boolean fullSpectra) {
        super(lineSpectra, fullSpectra);

        loader = new HierarchicalCoberturaCountXMLLoader<SourceCodeBlock, K>(packageSpectra, classSpectra,
                methodSpectra) {

            @Override
            public SourceCodeBlock getIdentifier(String packageName, String sourceFilePath, String methodNameAndSig,
                                                 int lineNumber, NodeType nodeType) {
                return new SourceCodeBlock(packageName, sourceFilePath, methodNameAndSig, lineNumber, nodeType);
            }

            @Override
            public int getNodeIndex(String sourceFilePath, int lineNumber, NodeType nodeType) {
                SourceCodeBlock identifier = new SourceCodeBlock(null, sourceFilePath, null, lineNumber, nodeType);
                INode<SourceCodeBlock> node = lineSpectra.getNode(identifier);
                if (node == null) {
                    return -1;
                } else {
                    return node.getIndex();
                }
            }

        };
    }

    @Override
    protected ICoverageDataLoader<SourceCodeBlock, K, CoberturaCoverageWrapper> getLoader() {
        return loader;
    }

    public boolean addData(String xmlFilePath, String identifier, boolean successful) {
        return super.addData(new CoberturaCoverageWrapper(new File(xmlFilePath), identifier, successful));
    }

}
