// Generated from Lqas.g4 by ANTLR 4.5
package lqas;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link LqasParser}.
 */
public interface LqasListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link LqasParser#start}.
	 * @param ctx the parse tree
	 */
	void enterStart(LqasParser.StartContext ctx);
	/**
	 * Exit a parse tree produced by {@link LqasParser#start}.
	 * @param ctx the parse tree
	 */
	void exitStart(LqasParser.StartContext ctx);
	/**
	 * Enter a parse tree produced by the {@code number}
	 * labeled alternative in {@link LqasParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterNumber(LqasParser.NumberContext ctx);
	/**
	 * Exit a parse tree produced by the {@code number}
	 * labeled alternative in {@link LqasParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitNumber(LqasParser.NumberContext ctx);
	/**
	 * Enter a parse tree produced by the {@code compare}
	 * labeled alternative in {@link LqasParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterCompare(LqasParser.CompareContext ctx);
	/**
	 * Exit a parse tree produced by the {@code compare}
	 * labeled alternative in {@link LqasParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitCompare(LqasParser.CompareContext ctx);
	/**
	 * Enter a parse tree produced by the {@code id}
	 * labeled alternative in {@link LqasParser#expr}.
	 * @param ctx the parse tree
	 */
	void enterId(LqasParser.IdContext ctx);
	/**
	 * Exit a parse tree produced by the {@code id}
	 * labeled alternative in {@link LqasParser#expr}.
	 * @param ctx the parse tree
	 */
	void exitId(LqasParser.IdContext ctx);
	/**
	 * Enter a parse tree produced by {@link LqasParser#binary_operator}.
	 * @param ctx the parse tree
	 */
	void enterBinary_operator(LqasParser.Binary_operatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link LqasParser#binary_operator}.
	 * @param ctx the parse tree
	 */
	void exitBinary_operator(LqasParser.Binary_operatorContext ctx);
}