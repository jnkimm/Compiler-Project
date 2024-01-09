package compiler;

public class varOffset {
	public Token variable;
	public int Offset;
//	public int lvIndex;
	
	public varOffset(Token var, int offset) {
		this.variable = var;
		this.Offset = offset;
	}
	
//	public void setIndex(int index) {
//		lvIndex = index;
//	}
}