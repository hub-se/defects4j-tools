package se.de.hu_berlin.informatik.faultlocalizer.ngram;

import java.util.HashSet;
import java.util.LinkedList;

public class LinearBlockSequence {
    private LinkedList<Integer> blockSeq;

    public LinearBlockSequence() {
        blockSeq = new LinkedList<>();
    }

    public LinkedList<Integer> getBlockSeq() {
        return blockSeq;
    }

    //counting all contained nodes
//    public int getAllNodeSize() {
//        return blockSeq.stream().mapToInt(LinearExecutionBlock::getSize).sum();
//    }

    public int getBlockSeqSize() {
        return blockSeq.size();
    }

    //    public List<ExecutionGraphNode> allNodes(){
//        return blockSeq.stream().
//                flatMap(block->block.getNodeSequence().stream()).collect(Collectors.toList());
//    }
    public HashSet<Integer> involvedBlocks() {
        HashSet<Integer> blocks = new HashSet<>(blockSeq.size());
        blockSeq.forEach(b -> blocks.add(b));
        return blocks;
    }

    public void addBlock(int blockID) {
        blockSeq.add(blockID);
    }

    @Override
    public String toString() {
        return "\t" + blockSeq;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof LinearBlockSequence)) {
            return false;
        }
        LinearBlockSequence dummy = (LinearBlockSequence) obj;
        return blockSeq.equals(dummy.blockSeq);
    }

    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + blockSeq.hashCode();
        return result;
    }
}
