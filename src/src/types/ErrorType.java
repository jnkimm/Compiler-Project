package types;

public class ErrorType extends TypeC {

    public String message;

    public ErrorType(String m) {
    	super(m);
    	message = "ErrorType("+message+")";
    }
}
