/**
 * 
 */
package se.de.hu_berlin.informatik.c2r.modules;

import java.util.Arrays;
import java.util.List;

import se.de.hu_berlin.informatik.stardust.localizer.SourceCodeBlock;
import se.de.hu_berlin.informatik.stardust.spectra.INode;
import se.de.hu_berlin.informatik.stardust.spectra.ISpectra;
import se.de.hu_berlin.informatik.stardust.spectra.ITrace;
import se.de.hu_berlin.informatik.utils.tm.moduleframework.AbstractModule;

/**
 * Reads a compressed spectra file and outputs a Spectra object.
 * 
 * @author Simon Heiden
 */
public class BuildBlockSpectraModule extends AbstractModule<ISpectra<SourceCodeBlock>, ISpectra<SourceCodeBlock>> {

	public BuildBlockSpectraModule() {
		super(true);
	}

	/* (non-Javadoc)
	 * @see se.de.hu_berlin.informatik.utils.tm.ITransmitter#processItem(java.lang.Object)
	 */
	@Override
	public ISpectra<SourceCodeBlock> processItem(final ISpectra<SourceCodeBlock> input) {
		
		//get lines in the spectra and sort them
		List<INode<SourceCodeBlock>> nodes = input.getNodes();
		SourceCodeBlock[] array = new SourceCodeBlock[nodes.size()];
		for (int i = 0; i < array.length; ++i) {
			array[i] = nodes.get(i).getIdentifier();
		}
		Arrays.sort(array);
		
		List<ITrace<SourceCodeBlock>> traces = input.getTraces();
		SourceCodeBlock lastLine = new SourceCodeBlock("", "", "", -1);
		INode<SourceCodeBlock> lastNode = null;
		//iterate over all lines
		for (SourceCodeBlock line : array) {
			INode<SourceCodeBlock> node = input.getNode(line);
			//see if we are inside the same method in the same package
			if (line.getMethodName().equals(lastLine.getMethodName())
					&& line.getPackageName().equals(lastLine.getPackageName())) {
				boolean isInvolvedInSameTraces = true;
				//see if the involvements match for consecutive nodes
				for (ITrace<SourceCodeBlock> trace : traces) {
					//if we find an involvement that doesn't match, then we can break the loop
					if (trace.isInvolved(node) != trace.isInvolved(lastNode)) {
						isInvolvedInSameTraces = false;
						break;
					}
				}
				//if this line is involved in the same traces as the last, then 
				//we can safely extend the last block to this line;
				//there cannot be any other covered lines in between due
				//to the ordering of SourceCodeLine objects
				if (isInvolvedInSameTraces) {
					//extend the range of the last block
					lastLine.setLineNumberEnd(line.getEndLineNumber());
					//remove the superfluous node from the spectra
					input.removeNode(line);
				} else {
					//if this line isn't involved in the same traces as the last 
					//one, then go on to the next line
					lastLine = line;
				}
			} else {
				//if we change into another method or package, also go
				//to the next line
				lastLine = line;
			}
		}
		
		return input;
	}

}
