package lqas;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

public class LqasAppEntry {
	
	private ParseTree getTree(String in) {
		
		ANTLRInputStream input = new ANTLRInputStream(in);
		LqasLexer lexer = new LqasLexer(input);
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		LqasParser parser = new LqasParser(tokens);
		ParseTree tree = parser.start();
		
		return tree;
	}
	
	public String getQuery(String in) {
		String query = null;
		
		ParseTree tree = getTree(in);
		ParseTreeWalker walker = new ParseTreeWalker();
		walker.walk(new LqasAppQueryGen(), tree);
		return query;
	}
}
