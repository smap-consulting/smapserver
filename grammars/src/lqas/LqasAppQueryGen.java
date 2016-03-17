package lqas;

public class LqasAppQueryGen extends LqasBaseListener {

	@Override
	public void enterNumber(LqasParser.NumberContext ctx) {
		System.out.println("This is a number: " + ctx.getText());
	} 
}
