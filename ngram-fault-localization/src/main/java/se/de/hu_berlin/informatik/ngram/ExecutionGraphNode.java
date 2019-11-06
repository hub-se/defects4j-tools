package se.de.hu_berlin.informatik.ngram;

import se.de.hu_berlin.informatik.spectra.core.ISpectra;
import se.de.hu_berlin.informatik.spectra.core.Node;

import java.util.HashSet;

/**
 * An execution graph node  consists structurally of three components.
 * The nodeId of this node in the spectra and two sets containing IDs
 * of other nodes that are executed before and after this node in the traces.
 */
public class ExecutionGraphNode extends Node {
    private HashSet<Integer> InNodes = new HashSet<>();
    private HashSet<Integer> OutNodes = new HashSet<>();
    private int nodeId;

    /**
     * Constructs the node
     *
     * @param index      the integer index of this node
     * @param identifier the identifier of this node
     * @param spectra
     */
    protected ExecutionGraphNode(int index, Object identifier, ISpectra spectra) {
        super(index, identifier, spectra);
    }


    public HashSet<Integer> getInNodes() {
        return InNodes;
    }

    public HashSet<Integer> getOutNodes() {
        return OutNodes;
    }

    public int getNodeId() {
        return nodeId;
    }

    public boolean checkInNode(Integer n) {
        return InNodes.contains(n);
    }

    public boolean addInNode(Integer n) {
        return InNodes.add(n);
    }

    public int getInDegree() {
        return InNodes.size();
    }

    public boolean checkOutNode(Integer n) {
        return OutNodes.contains(n);
    }

    public boolean addOutNode(Integer n) {
        return OutNodes.add(n);
    }

    public int getOutDegree() {
        return OutNodes.size();
    }

}