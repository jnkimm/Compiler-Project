package compiler;

import java.util.ArrayList;

public class Type {
	public Kind kind;
	public ArrayList<Integer> dims = new ArrayList<Integer>();
	
	public Type (Type t) {
		kind = t.kind;
		dims = t.dims;
	}
	
	public Type (Kind k) {
		kind = k;
	}
	
	public boolean equ(Type t) {
		if (t.kind == Kind.INT_VAL) {
			return (dims.size()== t.dims.size());
		} else {
			return (kind == t.kind) && (dims.size() == t.dims.size());
		}
	}
	
	@Override
	public String toString() {
		StringBuilder ret = new StringBuilder(kind.toString());
		for (int i = 0; i < dims.size(); i++) {
			ret.append("[");
			if (dims.get(i) > 0) {
				ret.append(dims.get(i).toString());
			}
			ret.append("]");
		}
		return ret.toString();
	}
}