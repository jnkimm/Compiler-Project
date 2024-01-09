package types;

public class BoolType extends TypeC {
    public BoolType(String m) {
    	super(m);
		// TODO Auto-generated constructor stub
	}

	public TypeC and (TypeC that) {
        if (that.getClass().equals(BoolType.class)) {
            return new BoolType("bool");
        } else {
            return new ErrorType("Cannot compute " + this.message + " and " + that.message + ".");
        }
    }

    public TypeC or (TypeC that) {
        if (that.getClass().equals(BoolType.class)) {
            return new BoolType("bool");
        } else {
            return new ErrorType("Cannot compute " + this.message  + " or " + that.message  + ".");
        }
    }

    public TypeC not () {
        return new BoolType("bool");
    }
    
    public TypeC assign (TypeC that) {
    	if (that.getClass().equals(BoolType.class)) {
    		return new BoolType("bool");
    	} else {
    		return new ErrorType("Cannot assign " + this.message  + " to " + that.message  + ".");
    	}
    }
}
