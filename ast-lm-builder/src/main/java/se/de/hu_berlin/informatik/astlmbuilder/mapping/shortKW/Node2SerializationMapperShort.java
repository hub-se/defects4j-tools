package se.de.hu_berlin.informatik.astlmbuilder.mapping.shortKW;

import java.util.Collection;
import java.util.List;

import com.github.javaparser.ast.Node;

import se.de.hu_berlin.informatik.astlmbuilder.ASTLMBOptions;
import se.de.hu_berlin.informatik.astlmbuilder.mapping.ITokenMapper;

/**
 * Adds the functionality of serialization to the experimental advanced node 2 string mapper
 *
 */
public class Node2SerializationMapperShort extends KeyWordConstantsShort implements ITokenMapper {
	
	private int maxSeriChildren = 0;
	
	/**
	 * Constructor without the setting for the maximum number of children to serialize.
	 * This is mostly used for testing.
	 */
	public Node2SerializationMapperShort() {
		// the parsing will not fail and needs no try/catch
		maxSeriChildren = Integer.parseInt( ASTLMBOptions.SERIALIZATION_MAX_CHILDREN_DEFAULT );
	}
	
	/**
	 * Constructor
	 * @param aMaxSerializationChildren The maximum number of children that should be
	 * included in the serialization of tokens. Use -1 to include all children for each node.
	 */
	public Node2SerializationMapperShort( int aMaxSerializationChildren ) {
		maxSeriChildren = aMaxSerializationChildren;
	}
	
	/**
	 * Adds serialization to the generation of tokens
	 */
	@Override
	public String getMappingForNode(Node aNode, int aSeriDepth) {
		
		StringBuilder result = new StringBuilder();
		serializeNode( aNode, result, aSeriDepth );
		
		return result.toString();
	}
	
	/**
	 * Builds the serialization string for a given node.
	 * This works recursive and stores the results in the StringBuilder object
	 * 
	 * @param aNode The node that should be serialized
	 * @param sBuilder The string builder that will be filled with the correct serialization
	 * @param aSeriDepth The depth for the serialization. 0 means only the keyword for the node type
	 * 	will be included and for each level a layer of children is added.
	 */
	private void serializeNode( Node aNode, StringBuilder aSBuilder, int aSeriDepth ) {
		if( aNode == null ) {
			return;
		}

		aSBuilder.append( BIG_GROUP_START );
		
		// get the keywords after the mapper did the dispatching and before it calls the mapping methods
		aSBuilder.append( KEYWORD_SERIALIZE + getMappingForNode( aNode, aSeriDepth ) );
		
		List<Node> children = aNode.getChildrenNodes();
		if ( aSeriDepth != 0 && children != null && children.size() > 0 ) {
			
			aSBuilder.append( GROUP_START );
					
			// get the serialization for all children with one depth less
			int upperBound = maxSeriChildren == -1 ?
					children.size()
					: Math.min( maxSeriChildren, children.size());
			
			for( int i = 0; i < upperBound; ++i ) {
				serializeNode( children.get( i ), aSBuilder, aSeriDepth - 1 );
				// do not append the split symbol after the last child
				if ( i != upperBound - 1 ) {
					aSBuilder.append( SPLIT );
				}
			}
			
			aSBuilder.append( GROUP_END );
		}
		
		aSBuilder.append( BIG_GROUP_END );
	}

	@Override
	public void setPrivMethodBlackList(Collection<String> aBL) {
		// this is not needed by the serialization
	}

	@Override
	public void clearPrivMethodBlackList() {
		// this is not needed by the serialization
	}
	
}